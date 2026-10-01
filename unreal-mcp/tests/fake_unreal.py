"""A stand-in for the Unreal Editor's side of the remote execution protocol.

It answers discovery pings, connects back when asked and runs commands with
plain Python, with a tiny fake ``unreal`` module installed.
"""

from __future__ import annotations

import contextlib
import io
import json
import socket
import sys
import threading
import traceback
import types
import uuid


def install_fake_unreal_module() -> types.ModuleType:
    mod = types.ModuleType("unreal")

    class _Base:
        pass

    for name in ("Object", "EnumBase", "Name", "Text", "Map", "StructBase", "LinearColor", "Rotator"):
        setattr(mod, name, type(name, (_Base,), {}))

    class Vector(_Base):
        def __init__(self, x=0.0, y=0.0, z=0.0):
            self.x, self.y, self.z = x, y, z

    mod.Vector = Vector
    mod.engine_version = "5.4.0-fake"
    sys.modules["unreal"] = mod
    return mod


class FakeUnreal:
    def __init__(self, project_name="FakeProject", group="239.0.0.1", port=6766):
        self.node_id = str(uuid.uuid4())
        self.project_name = project_name
        self.group, self.port = group, port
        self.commands: list[dict] = []
        self.namespace: dict = {"__name__": "__main__"}
        self._stop = threading.Event()
        self._udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self._udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "SO_REUSEPORT"):
            self._udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        self._udp.bind(("0.0.0.0", port))
        self._udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_LOOP, 1)
        self._udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 0)
        self._udp.setsockopt(
            socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, socket.inet_aton(group) + socket.inet_aton("0.0.0.0")
        )
        self._udp.settimeout(0.1)
        self._threads = [threading.Thread(target=self._udp_loop, daemon=True)]
        install_fake_unreal_module()

    def start(self):
        for t in self._threads:
            t.start()
        return self

    def stop(self):
        self._stop.set()
        for t in list(self._threads):
            t.join(timeout=2)
        self._udp.close()

    def _msg(self, type_, dest=None, data=None) -> bytes:
        m = {"version": 1, "magic": "ue_py", "type": type_, "source": self.node_id}
        if dest:
            m["dest"] = dest
        if data is not None:
            m["data"] = data
        return json.dumps(m).encode()

    def _udp_loop(self):
        while not self._stop.is_set():
            try:
                raw, _ = self._udp.recvfrom(65536)
            except (socket.timeout, OSError):
                continue
            msg = json.loads(raw)
            if msg["source"] == self.node_id or msg.get("dest") not in (None, self.node_id):
                continue
            if msg["type"] == "ping":
                pong = {"user": "tester", "machine": "localhost", "engine_version": "5.4.0",
                        "project_name": self.project_name, "project_root": "/tmp/fake"}
                self._udp.sendto(self._msg("pong", msg["source"], pong), (self.group, self.port))
            elif msg["type"] == "open_connection":
                d = msg["data"]
                t = threading.Thread(
                    target=self._tcp_loop, args=(msg["source"], d["command_ip"], d["command_port"]), daemon=True
                )
                self._threads.append(t)
                t.start()

    def _tcp_loop(self, client_id, ip, port):
        conn = socket.create_connection((ip, port))
        conn.settimeout(0.1)
        decoder, buf = json.JSONDecoder(), ""
        with conn:
            while not self._stop.is_set():
                try:
                    chunk = conn.recv(65536)
                except socket.timeout:
                    continue
                if not chunk:
                    return
                buf += chunk.decode()
                try:
                    msg, end = decoder.raw_decode(buf)
                except json.JSONDecodeError:
                    continue
                buf = buf[end:]
                self.commands.append(msg["data"])
                result = self._execute(msg["data"])
                conn.sendall(self._msg("command_result", client_id, result))

    def _execute(self, data):
        out = io.StringIO()
        success, result = True, "None"
        try:
            with contextlib.redirect_stdout(out):
                if data["exec_mode"] == "EvaluateStatement":
                    result = repr(eval(data["command"], self.namespace))
                else:
                    exec(data["command"], self.namespace)
        except Exception:
            success, result = False, traceback.format_exc()
        output = [{"type": "Info", "output": line} for line in out.getvalue().splitlines()]
        return {"success": success, "command": data["command"], "result": result, "output": output}
