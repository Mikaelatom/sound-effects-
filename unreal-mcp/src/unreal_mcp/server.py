"""MCP server that lets Claude drive a running Unreal Editor."""

from __future__ import annotations

import json
import logging
import os
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Annotated, Any

import anyio
from mcp.server.fastmcp import FastMCP, Image
from pydantic import Field

from . import editor_scripts as scripts
from .remote_execution import (
    MODE_EVAL_STATEMENT,
    MODE_EXEC_FILE,
    CommandResult,
    UnrealConnectionError,
    UnrealRemote,
)

log = logging.getLogger("unreal_mcp")

INSTRUCTIONS = """\
Tools for working inside a running Unreal Editor (UE5) through its Python API.

- Start with `unreal_project_info` to confirm the connection and see the open level.
- Prefer the dedicated tools for common work (actors, assets, properties, levels).
- For anything else, use `unreal_run_python`: the full `unreal` module is available,
  e.g. unreal.EditorAssetLibrary, unreal.EditorActorSubsystem, unreal.AssetToolsHelpers.
- Unreal units are centimetres; rotations are pitch/yaw/roll in degrees; Z is up.
- Asset paths look like /Game/Folder/AssetName (content browser paths).
- Changes are made in the live editor. Call `unreal_save_all` when the user wants work saved.
"""

mcp = FastMCP("unreal", instructions=INSTRUCTIONS)
_remote = UnrealRemote()


class UnrealError(RuntimeError):
    pass


def _format_failure(res: CommandResult) -> str:
    text = res.result.strip()
    output = res.output_text.strip()
    return "\n".join(part for part in (text, output) if part) or "Unreal reported a failure with no details."


async def _run(code: str, mode: str = MODE_EXEC_FILE, timeout: float | None = None) -> CommandResult:
    try:
        return await anyio.to_thread.run_sync(lambda: _remote.run(code, exec_mode=mode, timeout=timeout))
    except UnrealConnectionError as exc:
        raise UnrealError(str(exc)) from exc


async def _call(body: str, timeout: float | None = None, **args: Any) -> Any:
    """Run a snippet from editor_scripts and return its decoded result."""
    res = await _run(scripts.build_script(body, args), timeout=timeout)
    if not res.success:
        raise UnrealError(_format_failure(res))
    for entry in reversed(res.output):
        text = str(entry.get("output", ""))
        idx = text.find(scripts.RESULT_MARKER)
        if idx != -1:
            return json.loads(text[idx + len(scripts.RESULT_MARKER):].strip())
    raise UnrealError("Unreal ran the command but returned no result.\n" + res.output_text)


Vec3 = Annotated[list[float], Field(min_length=3, max_length=3)]


# -- connection ---------------------------------------------------------------


@mcp.tool()
async def unreal_list_instances() -> dict:
    """List running Unreal Editors that have Python remote execution enabled."""
    nodes = await anyio.to_thread.run_sync(_remote.discover)
    current = _remote.connected_node()
    return {
        "instances": [n.describe() for n in nodes],
        "connected_to": current.node_id if current else None,
    }


@mcp.tool()
async def unreal_select_instance(
    node_id: Annotated[str | None, Field(description="node_id from unreal_list_instances")] = None,
    project_name: Annotated[str | None, Field(description="Or pick the editor by project name")] = None,
) -> dict:
    """Choose which Unreal Editor to talk to when more than one is open."""
    _remote.preferred_node_id = node_id
    _remote.preferred_project = project_name
    try:
        node = await anyio.to_thread.run_sync(lambda: _remote.connect())
    except UnrealConnectionError as exc:
        raise UnrealError(str(exc)) from exc
    return node.describe()


# -- general -----------------------------------------------------------------


@mcp.tool()
async def unreal_project_info() -> dict:
    """Project name, paths, engine version, open level and actor count."""
    return await _call(scripts.PROJECT_INFO)


@mcp.tool()
async def unreal_run_python(
    code: Annotated[str, Field(description="Python source. `import unreal` to use the editor API. Use print() to report results.")],
    timeout_seconds: Annotated[float, Field(ge=1, le=3600)] = 300,
) -> dict:
    """Run arbitrary Python inside the Unreal Editor and return its printed output.

    Use this for anything the other tools don't cover: creating materials or
    Blueprints, batch-editing assets, querying the asset registry, editor
    utilities, etc. Errors come back with the Python traceback.
    """
    res = await _run(code, timeout=timeout_seconds)
    return {"success": res.success, "output": res.output_text, "result": res.result}


@mcp.tool()
async def unreal_evaluate(
    expression: Annotated[str, Field(description="A single Python expression, e.g. unreal.SystemLibrary.get_engine_version()")],
) -> dict:
    """Evaluate one Python expression in the editor and return its repr."""
    res = await _run(expression, mode=MODE_EVAL_STATEMENT)
    if not res.success:
        raise UnrealError(_format_failure(res))
    return {"result": res.result, "output": res.output_text}


@mcp.tool()
async def unreal_console_command(
    command: Annotated[str, Field(description="e.g. 'stat fps', 'r.ScreenPercentage 50', 'ke * MyEvent'")],
) -> str:
    """Run an Unreal console command in the editor world."""
    return await _call(scripts.CONSOLE_COMMAND, command=command)


# -- actors ------------------------------------------------------------------


@mcp.tool()
async def unreal_list_actors(
    class_filter: Annotated[str | None, Field(description="Substring of the class name, e.g. 'Light' or 'StaticMeshActor'")] = None,
    name_filter: Annotated[str | None, Field(description="Substring of the actor label")] = None,
    selected_only: bool = False,
    limit: Annotated[int, Field(ge=1, le=5000)] = 200,
) -> dict:
    """List actors in the open level with their class and transform."""
    return await _call(
        scripts.LIST_ACTORS,
        class_filter=class_filter,
        name_filter=name_filter,
        selected_only=selected_only,
        limit=limit,
    )


@mcp.tool()
async def unreal_get_actor(
    actor: Annotated[str, Field(description="Actor label (as shown in the Outliner) or object name")],
) -> dict:
    """Details of one actor: transform, tags and components."""
    return await _call(scripts.GET_ACTOR, actor=actor)


@mcp.tool()
async def unreal_spawn_actor(
    what: Annotated[
        str,
        Field(
            description="Either an actor class name from the unreal module (PointLight, StaticMeshActor, "
            "CameraActor, ...) or an asset path such as /Game/Meshes/SM_Rock, a Blueprint "
            "/Game/BP/BP_Door or a sound /Game/Audio/Explosion."
        ),
    ],
    location: Vec3 | None = None,
    rotation: Annotated[Vec3 | None, Field(description="[pitch, yaw, roll] in degrees")] = None,
    scale: Vec3 | None = None,
    label: Annotated[str | None, Field(description="Name shown in the Outliner")] = None,
    folder: Annotated[str | None, Field(description="Outliner folder, e.g. 'Lighting'")] = None,
) -> dict:
    """Spawn an actor in the open level from a class or an asset."""
    return await _call(
        scripts.SPAWN_ACTOR,
        what=what,
        location=location,
        rotation=rotation,
        scale=scale,
        label=label,
        folder=folder,
    )


@mcp.tool()
async def unreal_transform_actor(
    actor: str,
    location: Vec3 | None = None,
    rotation: Annotated[Vec3 | None, Field(description="[pitch, yaw, roll] in degrees")] = None,
    scale: Vec3 | None = None,
    new_label: str | None = None,
) -> dict:
    """Move, rotate, scale and/or rename an actor. Omitted values are left unchanged."""
    return await _call(
        scripts.TRANSFORM_ACTOR,
        actor=actor,
        location=location,
        rotation=rotation,
        scale=scale,
        new_label=new_label,
    )


@mcp.tool()
async def unreal_delete_actors(actors: list[str]) -> dict:
    """Delete actors from the open level by label or name."""
    return await _call(scripts.DELETE_ACTORS, actors=actors)


@mcp.tool()
async def unreal_select_actors(
    actors: list[str],
    focus: Annotated[bool, Field(description="Also move the viewport camera to the selection")] = False,
) -> list:
    """Select actors in the editor (replaces the current selection)."""
    return await _call(scripts.SELECT_ACTORS, actors=actors, focus=focus)


# -- properties --------------------------------------------------------------


@mcp.tool()
async def unreal_get_property(
    property: Annotated[str, Field(description="Editor property name in snake_case, e.g. 'intensity', 'static_mesh'")],
    actor: Annotated[str | None, Field(description="Actor label (use this or asset)")] = None,
    asset: Annotated[str | None, Field(description="Asset path (use this or actor)")] = None,
    component: Annotated[str | None, Field(description="Component name on the actor, e.g. 'LightComponent0'")] = None,
) -> dict:
    """Read a property from an actor, one of its components, or an asset."""
    return await _call(scripts.GET_PROPERTY, property=property, actor=actor, asset=asset, component=component)


@mcp.tool()
async def unreal_set_property(
    property: Annotated[str, Field(description="Editor property name in snake_case")],
    value: Annotated[
        Any,
        Field(
            description="JSON value. Vectors/colors as lists, rotators as [pitch,yaw,roll], enums by "
            "name, object references as asset paths."
        ),
    ],
    actor: str | None = None,
    asset: str | None = None,
    component: str | None = None,
    save: Annotated[bool, Field(description="Save the asset afterwards (assets only)")] = True,
) -> dict:
    """Set a property on an actor, one of its components, or an asset."""
    return await _call(
        scripts.SET_PROPERTY,
        property=property,
        value=value,
        actor=actor,
        asset=asset,
        component=component,
        save=save,
    )


# -- assets & levels ---------------------------------------------------------


@mcp.tool()
async def unreal_list_assets(
    path: Annotated[str, Field(description="Content folder, e.g. /Game or /Game/Audio")] = "/Game",
    recursive: bool = True,
    class_filter: Annotated[str | None, Field(description="Substring of the asset class, e.g. 'SoundWave', 'Material'")] = None,
    name_filter: str | None = None,
    limit: Annotated[int, Field(ge=1, le=5000)] = 200,
) -> dict:
    """List assets in the content browser."""
    return await _call(
        scripts.LIST_ASSETS,
        path=path,
        recursive=recursive,
        class_filter=class_filter,
        name_filter=name_filter,
        limit=limit,
    )


@mcp.tool()
async def unreal_import_files(
    files: Annotated[list[str], Field(description="Absolute paths on this computer (.wav, .fbx, .png, ...)")],
    destination: Annotated[str, Field(description="Content folder to import into")] = "/Game/Imported",
    replace_existing: bool = True,
) -> list:
    """Import files from disk into the project (sounds, meshes, textures, ...)."""
    resolved = []
    for f in files:
        p = Path(f).expanduser().resolve()
        if not p.is_file():
            raise UnrealError(f"File not found: {p}")
        resolved.append(p.as_posix())
    return await _call(
        scripts.IMPORT_FILES, files=resolved, destination=destination, replace_existing=replace_existing
    )


@mcp.tool()
async def unreal_open_level(
    level: Annotated[str, Field(description="Level asset path, e.g. /Game/Maps/MainMenu")],
) -> str:
    """Open a level in the editor (unsaved changes in the current level may prompt in the editor)."""
    return await _call(scripts.OPEN_LEVEL, level=level)


@mcp.tool()
async def unreal_save_all() -> bool:
    """Save every modified level and asset."""
    return await _call(scripts.SAVE_ALL)


@mcp.tool()
async def unreal_screenshot(
    width: Annotated[int, Field(ge=64, le=3840)] = 1280,
    height: Annotated[int, Field(ge=64, le=2160)] = 720,
) -> Image:
    """Capture the active editor viewport so you can see the scene."""
    out_dir = Path(os.environ.get("UNREAL_MCP_SCREENSHOT_DIR") or tempfile.gettempdir()) / "unreal-mcp"
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / f"shot-{uuid.uuid4().hex[:8]}.png"
    await _call(scripts.SCREENSHOT, width=width, height=height, filename=target.as_posix())

    # Unreal writes the file on a later frame, after our command has returned.
    deadline = time.monotonic() + 20
    last_size = -1
    while time.monotonic() < deadline:
        if target.exists():
            size = target.stat().st_size
            if size > 0 and size == last_size:
                return Image(path=target)
            last_size = size
        await anyio.sleep(0.25)
    raise UnrealError(
        f"Screenshot was requested but {target} never appeared. Make sure a level viewport is visible."
    )


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("UNREAL_MCP_LOG_LEVEL", "INFO"),
        stream=sys.stderr,  # stdout is reserved for the MCP protocol
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        mcp.run()
    finally:
        _remote.close()


if __name__ == "__main__":
    main()
