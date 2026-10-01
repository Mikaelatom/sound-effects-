"""Client for Unreal Engine's Python remote execution protocol.

This is the same protocol Unreal's own ``remote_execution.py`` (shipped with the
Python Editor Script Plugin) speaks:

1. Discovery happens over UDP multicast. We send a ``ping``; every editor with
   "Enable Remote Execution" turned on answers with a ``pong`` describing itself.
2. To run commands we open a TCP listener and send ``open_connection`` to the
   chosen editor. The editor connects back to us.
3. ``command`` messages go over that TCP socket and the editor answers with a
   ``command_result`` containing success, the result and captured log output.

Every message is a single JSON object, UTF-8 encoded.
"""

from __future__ import annotations

import json
import logging
import os
import socket
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

log = logging.getLogger(__name__)

PROTOCOL_VERSION = 1
PROTOCOL_MAGIC = "ue_py"

TYPE_PING = "ping"
TYPE_PONG = "pong"
TYPE_OPEN_CONNECTION = "open_connection"
TYPE_CLOSE_CONNECTION = "close_connection"
TYPE_COMMAND = "command"
TYPE_COMMAND_RESULT = "command_result"

# Execution modes understood by Unreal.
MODE_EXEC_FILE = "ExecuteFile"  # a file path, or a literal multi-statement script
MODE_EXEC_STATEMENT = "ExecuteStatement"  # a single statement
MODE_EVAL_STATEMENT = "EvaluateStatement"  # a single expression, result is returned

_RECV_SIZE = 65536


class UnrealConnectionError(RuntimeError):
    """Raised when no editor can be found or the connection drops."""


@dataclass
class RemoteConfig:
    """Network settings. Defaults match Unreal's Project Settings > Python defaults."""

    multicast_group: str = "239.0.0.1"
    multicast_port: int = 6766
    multicast_bind_address: str = "0.0.0.0"
    multicast_ttl: int = 0  # 0 = this machine only
    command_host: str = "127.0.0.1"
    command_port: int = 0  # 0 = pick any free port; Unreal connects to whatever we tell it
    discovery_timeout: float = 1.5
    connect_timeout: float = 5.0
    command_timeout: float = 300.0

    @classmethod
    def from_env(cls) -> "RemoteConfig":
        cfg = cls()
        env = os.environ.get
        cfg.multicast_group = env("UNREAL_MCP_MULTICAST_GROUP", cfg.multicast_group)
        cfg.multicast_port = int(env("UNREAL_MCP_MULTICAST_PORT", cfg.multicast_port))
        cfg.multicast_bind_address = env("UNREAL_MCP_MULTICAST_BIND", cfg.multicast_bind_address)
        cfg.multicast_ttl = int(env("UNREAL_MCP_MULTICAST_TTL", cfg.multicast_ttl))
        cfg.command_host = env("UNREAL_MCP_COMMAND_HOST", cfg.command_host)
        cfg.command_port = int(env("UNREAL_MCP_COMMAND_PORT", cfg.command_port))
        cfg.command_timeout = float(env("UNREAL_MCP_COMMAND_TIMEOUT", cfg.command_timeout))
        return cfg


@dataclass
class UnrealNode:
    """An editor instance that answered discovery."""

    node_id: str
    data: dict[str, Any] = field(default_factory=dict)

    @property
    def project_name(self) -> str:
        return str(self.data.get("project_name", ""))

    def describe(self) -> dict[str, Any]:
        return {"node_id": self.node_id, **self.data}


@dataclass
class CommandResult:
    success: bool
    result: str
    output: list[dict[str, str]]

    @property
    def output_text(self) -> str:
        return "\n".join(str(entry.get("output", "")) for entry in self.output)


class UnrealRemote:
    """Discovers editors and runs Python in one of them. Thread-safe."""

    def __init__(self, config: RemoteConfig | None = None):
        self.config = config or RemoteConfig.from_env()
        self.node_id = str(uuid.uuid4())
        self._lock = threading.RLock()
        self._udp: socket.socket | None = None
        self._tcp: socket.socket | None = None
        self._connected_node: UnrealNode | None = None
        self.preferred_node_id: str | None = None
        self.preferred_project: str | None = os.environ.get("UNREAL_MCP_PROJECT") or None

    # -- message helpers -------------------------------------------------

    def _message(self, type_: str, dest: str | None = None, data: dict | None = None) -> bytes:
        msg: dict[str, Any] = {
            "version": PROTOCOL_VERSION,
            "magic": PROTOCOL_MAGIC,
            "type": type_,
            "source": self.node_id,
        }
        if dest:
            msg["dest"] = dest
        if data is not None:
            msg["data"] = data
        return json.dumps(msg, ensure_ascii=False).encode("utf-8")

    def _parse(self, raw: bytes) -> dict | None:
        try:
            msg = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return None
        return self._accept(msg)

    def _accept(self, msg: Any) -> dict | None:
        """Return ``msg`` if it is a protocol message meant for us, else None."""
        if not isinstance(msg, dict):
            return None
        if msg.get("version") != PROTOCOL_VERSION or msg.get("magic") != PROTOCOL_MAGIC:
            return None
        if msg.get("source") == self.node_id:
            return None  # our own multicast echo
        dest = msg.get("dest")
        if dest and dest != self.node_id:
            return None
        return msg

    # -- UDP discovery ---------------------------------------------------

    def _udp_socket(self) -> socket.socket:
        if self._udp is not None:
            return self._udp
        cfg = self.config
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "SO_REUSEPORT"):
            try:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            except OSError:
                pass
        sock.bind((cfg.multicast_bind_address, cfg.multicast_port))
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, cfg.multicast_ttl)
        membership = socket.inet_aton(cfg.multicast_group) + socket.inet_aton(cfg.multicast_bind_address)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership)
        sock.settimeout(0.1)
        self._udp = sock
        return sock

    def _broadcast(self, payload: bytes) -> None:
        self._udp_socket().sendto(payload, (self.config.multicast_group, self.config.multicast_port))

    def discover(self, timeout: float | None = None) -> list[UnrealNode]:
        """Ping the multicast group and return every editor that answers."""
        timeout = self.config.discovery_timeout if timeout is None else timeout
        with self._lock:
            sock = self._udp_socket()
            self._broadcast(self._message(TYPE_PING))
            nodes: dict[str, UnrealNode] = {}
            deadline = time.monotonic() + timeout
            last_ping = time.monotonic()
            while time.monotonic() < deadline:
                # Re-ping once in case the first packet was missed.
                if time.monotonic() - last_ping > 0.5:
                    self._broadcast(self._message(TYPE_PING))
                    last_ping = time.monotonic()
                try:
                    raw, _ = sock.recvfrom(_RECV_SIZE)
                except socket.timeout:
                    continue
                msg = self._parse(raw)
                if msg and msg.get("type") == TYPE_PONG:
                    nodes[msg["source"]] = UnrealNode(msg["source"], msg.get("data") or {})
            return list(nodes.values())

    def _choose(self, nodes: list[UnrealNode]) -> UnrealNode:
        if not nodes:
            raise UnrealConnectionError(
                "No Unreal Editor found. Make sure the editor is running, the 'Python Editor Script "
                "Plugin' is enabled, and Project Settings > Plugins > Python > 'Enable Remote "
                "Execution' is checked."
            )
        if self.preferred_node_id:
            for node in nodes:
                if node.node_id == self.preferred_node_id:
                    return node
        if self.preferred_project:
            for node in nodes:
                if node.project_name.lower() == self.preferred_project.lower():
                    return node
        return nodes[0]

    # -- TCP command channel --------------------------------------------

    def connected_node(self) -> UnrealNode | None:
        return self._connected_node if self._tcp else None

    def connect(self, node: UnrealNode | None = None) -> UnrealNode:
        with self._lock:
            if node is None:
                node = self._choose(self.discover())
            if self._tcp and self._connected_node and self._connected_node.node_id == node.node_id:
                return node
            self.disconnect()
            cfg = self.config
            listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind((cfg.command_host, cfg.command_port))
            listener.listen(1)
            listener.settimeout(cfg.connect_timeout)
            host, port = listener.getsockname()
            try:
                self._broadcast(
                    self._message(
                        TYPE_OPEN_CONNECTION,
                        dest=node.node_id,
                        data={"command_ip": host, "command_port": port},
                    )
                )
                try:
                    conn, _ = listener.accept()
                except socket.timeout as exc:
                    raise UnrealConnectionError(
                        f"Unreal ({node.project_name or node.node_id}) did not open the command "
                        "connection in time."
                    ) from exc
            finally:
                listener.close()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            self._tcp = conn
            self._connected_node = node
            log.info("Connected to Unreal project %r (%s)", node.project_name, node.node_id)
            return node

    def disconnect(self) -> None:
        with self._lock:
            if self._tcp is not None:
                try:
                    if self._connected_node:
                        self._broadcast(self._message(TYPE_CLOSE_CONNECTION, dest=self._connected_node.node_id))
                except OSError:
                    pass
                try:
                    self._tcp.close()
                except OSError:
                    pass
            self._tcp = None
            self._connected_node = None

    def close(self) -> None:
        with self._lock:
            self.disconnect()
            if self._udp is not None:
                self._udp.close()
                self._udp = None

    def _receive_result(self, sock: socket.socket, timeout: float) -> dict:
        decoder = json.JSONDecoder()
        buffer = ""
        pending = b""
        deadline = time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(f"Unreal did not answer within {timeout:.0f}s")
            sock.settimeout(min(remaining, 1.0))
            try:
                chunk = sock.recv(_RECV_SIZE)
            except socket.timeout:
                continue
            if not chunk:
                raise UnrealConnectionError("Unreal closed the command connection.")
            pending += chunk
            try:
                buffer += pending.decode("utf-8")
                pending = b""
            except UnicodeDecodeError:
                continue  # a multi-byte character was split across reads
            while buffer:
                stripped = buffer.lstrip()
                try:
                    obj, end = decoder.raw_decode(stripped)
                except json.JSONDecodeError:
                    break  # need more data
                buffer = stripped[end:]
                msg = self._accept(obj)
                if msg and msg.get("type") == TYPE_COMMAND_RESULT:
                    return msg.get("data") or {}

    def run(
        self,
        command: str,
        exec_mode: str = MODE_EXEC_FILE,
        unattended: bool = True,
        timeout: float | None = None,
    ) -> CommandResult:
        """Run Python inside the editor, reconnecting once if the editor went away."""
        timeout = self.config.command_timeout if timeout is None else timeout
        with self._lock:
            for attempt in range(2):
                node = self.connected_node() or self.connect()
                payload = self._message(
                    TYPE_COMMAND,
                    dest=node.node_id,
                    data={"command": command, "unattended": unattended, "exec_mode": exec_mode},
                )
                try:
                    assert self._tcp is not None
                    self._tcp.sendall(payload)
                    data = self._receive_result(self._tcp, timeout)
                except (OSError, UnrealConnectionError):
                    self.disconnect()
                    if attempt == 0:
                        log.info("Command connection lost; rediscovering Unreal and retrying")
                        continue
                    raise
                except TimeoutError:
                    # The socket may now hold a late reply; start fresh next time.
                    self.disconnect()
                    raise
                return CommandResult(
                    success=bool(data.get("success")),
                    result=str(data.get("result", "")),
                    output=list(data.get("output") or []),
                )
        raise UnrealConnectionError("unreachable")
