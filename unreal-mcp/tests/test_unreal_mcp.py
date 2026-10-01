import asyncio
import json

import pytest

from fake_unreal import FakeUnreal
from unreal_mcp import editor_scripts, server
from unreal_mcp.remote_execution import MODE_EVAL_STATEMENT, RemoteConfig, UnrealRemote

PORT = 16766  # avoid clashing with a real editor on the default port


@pytest.fixture
def editor():
    fake = FakeUnreal(port=PORT).start()
    yield fake
    fake.stop()


@pytest.fixture
def remote():
    r = UnrealRemote(RemoteConfig(multicast_port=PORT, discovery_timeout=0.6))
    yield r
    r.close()


def test_discovery_finds_editor(editor, remote):
    nodes = remote.discover()
    assert [n.node_id for n in nodes] == [editor.node_id]
    assert nodes[0].project_name == "FakeProject"


def test_run_and_evaluate(editor, remote):
    res = remote.run("x = 40\nprint('hello from unreal')")
    assert res.success and res.output_text == "hello from unreal"
    res = remote.run("x + 2", exec_mode=MODE_EVAL_STATEMENT)
    assert res.result == "42"


def test_errors_return_traceback(editor, remote):
    res = remote.run("raise ValueError('boom')")
    assert not res.success
    assert "ValueError: boom" in res.result


def test_large_unicode_output(editor, remote):
    res = remote.run("print('é' * 200000)")
    assert res.success and len(res.output_text) == 200000


def test_reconnects_after_editor_restart(remote):
    first = FakeUnreal(port=PORT).start()
    try:
        assert remote.run("print(1)").success
    finally:
        first.stop()
    second = FakeUnreal(port=PORT).start()
    try:
        assert remote.run("print(2)").output_text == "2"
        assert remote.connected_node().node_id == second.node_id
    finally:
        second.stop()


def test_picks_editor_by_project(remote):
    a = FakeUnreal("Alpha", port=PORT).start()
    b = FakeUnreal("Beta", port=PORT).start()
    try:
        remote.preferred_project = "beta"
        assert remote.connect().node_id == b.node_id
    finally:
        a.stop()
        b.stop()


def test_no_editor_gives_helpful_error(remote):
    with pytest.raises(Exception, match="Enable Remote Execution"):
        remote.connect()


@pytest.mark.parametrize("name", sorted(editor_scripts.ALL_SNIPPETS))
def test_editor_snippets_compile(name):
    compile(editor_scripts.build_script(editor_scripts.ALL_SNIPPETS[name], {"a": 1}), name, "exec")


def test_server_call_roundtrip(editor, remote, monkeypatch):
    monkeypatch.setattr(server, "_remote", remote)
    body = "return {'echo': args['text'], 'vec': unreal.Vector(1, 2, 3)}"
    result = asyncio.run(server._call(body, text='quotes " and \\n newlines'))
    assert result == {"echo": 'quotes " and \\n newlines', "vec": [1, 2, 3]}
    # Arguments travel as JSON data, never spliced into code.
    assert json.dumps('quotes " and') not in editor.commands[-1]["command"]


def test_server_call_raises_unreal_errors(editor, remote, monkeypatch):
    monkeypatch.setattr(server, "_remote", remote)
    with pytest.raises(server.UnrealError, match="No such thing"):
        asyncio.run(server._call("raise ValueError('No such thing')"))


def test_tools_registered():
    names = {t.name for t in asyncio.run(server.mcp.list_tools())}
    assert {"unreal_run_python", "unreal_spawn_actor", "unreal_import_files", "unreal_screenshot"} <= names
