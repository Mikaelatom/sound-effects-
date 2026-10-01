"""Python snippets that run *inside* the Unreal Editor.

Each snippet is the body of a function ``main(args)`` that returns a
JSON-serialisable value. :func:`build_script` wraps a body with shared helpers
and prints the return value after a marker so the MCP server can find it in
the editor's log output.
"""

from __future__ import annotations

import json
import textwrap

RESULT_MARKER = "__UNREAL_MCP_RESULT__:"

# Helpers available to every snippet.
PRELUDE = r'''
import json as _json
import unreal

def _js(v, depth=0):
    """Convert Unreal values into plain JSON-friendly data."""
    if depth > 6:
        return str(v)
    if v is None or isinstance(v, (bool, int, float, str)):
        return v
    if isinstance(v, unreal.Vector):
        return [v.x, v.y, v.z]
    if isinstance(v, unreal.Rotator):
        return {"pitch": v.pitch, "yaw": v.yaw, "roll": v.roll}
    if isinstance(v, unreal.LinearColor):
        return [v.r, v.g, v.b, v.a]
    if isinstance(v, unreal.Object):
        return v.get_path_name()
    if isinstance(v, unreal.EnumBase):
        return v.name
    if isinstance(v, (unreal.Name, unreal.Text)):
        return str(v)
    if isinstance(v, unreal.Map):
        return {str(k): _js(x, depth + 1) for k, x in v.items()}
    if isinstance(v, unreal.StructBase):
        try:
            return v.export_text()
        except Exception:
            return str(v)
    if isinstance(v, dict):
        return {str(k): _js(x, depth + 1) for k, x in v.items()}
    try:
        return [_js(x, depth + 1) for x in v]
    except TypeError:
        return str(v)

def _actors():
    return unreal.get_editor_subsystem(unreal.EditorActorSubsystem)

def _world():
    return unreal.get_editor_subsystem(unreal.UnrealEditorSubsystem).get_editor_world()

def _find_actor(ident):
    actors = _actors().get_all_level_actors()
    for test in (lambda a: a.get_actor_label() == ident,
                 lambda a: a.get_name() == ident,
                 lambda a: a.get_path_name() == ident,
                 lambda a: a.get_actor_label().lower() == str(ident).lower()):
        for a in actors:
            if test(a):
                return a
    raise ValueError("No actor with label/name %r in the current level" % (ident,))

def _vec(v):
    return unreal.Vector(float(v[0]), float(v[1]), float(v[2]))

def _rot(r):
    if isinstance(r, dict):
        return unreal.Rotator(pitch=float(r.get("pitch", 0)), yaw=float(r.get("yaw", 0)), roll=float(r.get("roll", 0)))
    return unreal.Rotator(pitch=float(r[0]), yaw=float(r[1]), roll=float(r[2]))

def _actor_summary(a):
    return {
        "label": a.get_actor_label(),
        "name": a.get_name(),
        "class": a.get_class().get_name(),
        "location": _js(a.get_actor_location()),
        "rotation": _js(a.get_actor_rotation()),
        "scale": _js(a.get_actor_scale3d()),
        "folder": str(a.get_folder_path()),
    }

def _target(actor=None, asset=None, component=None):
    if asset:
        obj = unreal.load_asset(asset)
        if obj is None:
            raise ValueError("Could not load asset %r" % (asset,))
        return obj
    obj = _find_actor(actor)
    if component:
        for c in obj.get_components_by_class(unreal.ActorComponent):
            if c.get_name() == component:
                return c
        raise ValueError("Actor %r has no component named %r" % (actor, component))
    return obj

def _coerce(current, value):
    """Turn a JSON value into something set_editor_property will accept."""
    if isinstance(current, unreal.Vector) and isinstance(value, (list, tuple)):
        return _vec(value)
    if isinstance(current, unreal.Rotator) and isinstance(value, (list, tuple, dict)):
        return _rot(value)
    if isinstance(current, unreal.LinearColor) and isinstance(value, (list, tuple)):
        return unreal.LinearColor(*[float(x) for x in value])
    if isinstance(current, unreal.EnumBase) and isinstance(value, str):
        return getattr(type(current), value.upper())
    if isinstance(value, str) and value.startswith("/") and (current is None or isinstance(current, unreal.Object)):
        loaded = unreal.load_asset(value) or unreal.load_object(None, value)
        if loaded is None:
            raise ValueError("Could not load %r" % (value,))
        return loaded
    if isinstance(current, unreal.Name):
        return unreal.Name(value)
    if isinstance(current, unreal.Text):
        return unreal.Text(value)
    return value
'''


def build_script(body: str, args: dict | None = None) -> str:
    """Wrap a snippet body into a complete script for Unreal's ExecuteFile mode."""
    encoded_args = json.dumps(json.dumps(args or {}))
    return (
        PRELUDE
        + "\ndef __unreal_mcp_main(args):\n"
        + textwrap.indent(textwrap.dedent(body).strip("\n"), "    ")
        + f"\n\nprint({RESULT_MARKER!r} + _json.dumps(_js(__unreal_mcp_main(_json.loads({encoded_args})))))\n"
    )


PROJECT_INFO = r'''
world = _world()
return {
    "project_name": unreal.SystemLibrary.get_game_name(),
    "project_dir": unreal.Paths.convert_relative_path_to_full(unreal.Paths.project_dir()),
    "content_dir": unreal.Paths.convert_relative_path_to_full(unreal.Paths.project_content_dir()),
    "engine_version": unreal.SystemLibrary.get_engine_version(),
    "current_level": world.get_path_name() if world else None,
    "actor_count": len(_actors().get_all_level_actors()),
}
'''

LIST_ACTORS = r'''
sub = _actors()
actors = sub.get_selected_level_actors() if args["selected_only"] else sub.get_all_level_actors()
cls = (args.get("class_filter") or "").lower()
name = (args.get("name_filter") or "").lower()
out = []
for a in actors:
    if cls and cls not in a.get_class().get_name().lower():
        continue
    if name and name not in a.get_actor_label().lower() and name not in a.get_name().lower():
        continue
    out.append(_actor_summary(a))
total = len(out)
return {"total": total, "actors": out[: args["limit"]]}
'''

GET_ACTOR = r'''
a = _find_actor(args["actor"])
info = _actor_summary(a)
info["path"] = a.get_path_name()
info["tags"] = [str(t) for t in a.tags]
info["components"] = [
    {"name": c.get_name(), "class": c.get_class().get_name()}
    for c in a.get_components_by_class(unreal.ActorComponent)
]
return info
'''

SPAWN_ACTOR = r'''
what = args["what"]
loc = _vec(args.get("location") or [0, 0, 0])
rot = _rot(args.get("rotation") or [0, 0, 0])
sub = _actors()
if what.startswith("/"):
    obj = unreal.load_asset(what)
    if obj is None:
        obj = unreal.load_class(None, what)
    if obj is None:
        raise ValueError("Could not load %r" % (what,))
    if isinstance(obj, unreal.Blueprint):
        obj = unreal.EditorAssetLibrary.load_blueprint_class(what)
    if isinstance(obj, unreal.Class):
        actor = sub.spawn_actor_from_class(obj, loc, rot)
    else:
        # Meshes, sounds, particle systems... get wrapped in a suitable actor.
        actor = sub.spawn_actor_from_object(obj, loc, rot)
else:
    cls = getattr(unreal, what, None)
    if cls is None:
        raise ValueError("Unknown actor class %r (use e.g. 'PointLight', 'StaticMeshActor' or an asset path)" % (what,))
    actor = sub.spawn_actor_from_class(cls, loc, rot)
if actor is None:
    raise RuntimeError("Unreal refused to spawn %r" % (what,))
if args.get("scale"):
    actor.set_actor_scale3d(_vec(args["scale"]))
if args.get("label"):
    actor.set_actor_label(args["label"])
if args.get("folder"):
    actor.set_folder_path(args["folder"])
return _actor_summary(actor)
'''

TRANSFORM_ACTOR = r'''
a = _find_actor(args["actor"])
if args.get("location") is not None:
    a.set_actor_location(_vec(args["location"]), False, True)
if args.get("rotation") is not None:
    a.set_actor_rotation(_rot(args["rotation"]), True)
if args.get("scale") is not None:
    a.set_actor_scale3d(_vec(args["scale"]))
if args.get("new_label"):
    a.set_actor_label(args["new_label"])
return _actor_summary(a)
'''

DELETE_ACTORS = r'''
deleted, missing = [], []
for ident in args["actors"]:
    try:
        a = _find_actor(ident)
    except ValueError:
        missing.append(ident)
        continue
    label = a.get_actor_label()
    if _actors().destroy_actor(a):
        deleted.append(label)
    else:
        missing.append(ident)
return {"deleted": deleted, "not_deleted": missing}
'''

SELECT_ACTORS = r'''
found = [_find_actor(i) for i in args["actors"]]
_actors().set_selected_level_actors(found)
if found and args.get("focus"):
    unreal.SystemLibrary.execute_console_command(_world(), "CAMERA ALIGN ACTIVEVIEWPORTONLY")
return [a.get_actor_label() for a in found]
'''

GET_PROPERTY = r'''
obj = _target(args.get("actor"), args.get("asset"), args.get("component"))
return {"object": obj.get_path_name(), "property": args["property"], "value": obj.get_editor_property(args["property"])}
'''

SET_PROPERTY = r'''
obj = _target(args.get("actor"), args.get("asset"), args.get("component"))
prop = args["property"]
try:
    current = obj.get_editor_property(prop)
except Exception:
    current = None
obj.set_editor_property(prop, _coerce(current, args["value"]))
if args.get("asset") and args.get("save", True):
    unreal.EditorAssetLibrary.save_loaded_asset(obj)
return {"object": obj.get_path_name(), "property": prop, "value": obj.get_editor_property(prop)}
'''

LIST_ASSETS = r'''
registry = unreal.AssetRegistryHelpers.get_asset_registry()
assets = registry.get_assets_by_path(args["path"], recursive=args["recursive"])
cls = (args.get("class_filter") or "").lower()
name = (args.get("name_filter") or "").lower()
out = []
for ad in assets:
    try:
        class_name = str(ad.asset_class_path.asset_name)
    except AttributeError:  # UE 4.x / early 5.0
        class_name = str(ad.asset_class)
    if cls and cls not in class_name.lower():
        continue
    if name and name not in str(ad.asset_name).lower():
        continue
    out.append({"path": str(ad.package_name) + "." + str(ad.asset_name), "class": class_name})
out.sort(key=lambda x: x["path"])
return {"total": len(out), "assets": out[: args["limit"]]}
'''

IMPORT_FILES = r'''
tasks = []
for f in args["files"]:
    t = unreal.AssetImportTask()
    t.set_editor_property("filename", f)
    t.set_editor_property("destination_path", args["destination"])
    t.set_editor_property("automated", True)
    t.set_editor_property("replace_existing", args["replace_existing"])
    t.set_editor_property("save", True)
    tasks.append(t)
unreal.AssetToolsHelpers.get_asset_tools().import_asset_tasks(tasks)
return [{"file": t.get_editor_property("filename"), "imported": [str(p) for p in t.get_editor_property("imported_object_paths")]} for t in tasks]
'''

CONSOLE_COMMAND = r'''
unreal.SystemLibrary.execute_console_command(_world(), args["command"])
return "ok"
'''

OPEN_LEVEL = r'''
ok = unreal.get_editor_subsystem(unreal.LevelEditorSubsystem).load_level(args["level"])
if not ok:
    raise RuntimeError("Could not open level %r" % (args["level"],))
return _world().get_path_name()
'''

SAVE_ALL = r'''
return unreal.EditorLoadingAndSavingUtils.save_dirty_packages(True, True)
'''

SCREENSHOT = r'''
try:
    unreal.AutomationLibrary.take_high_res_screenshot(args["width"], args["height"], args["filename"])
except Exception:
    unreal.SystemLibrary.execute_console_command(
        _world(), "HighResShot %dx%d filename=\"%s\"" % (args["width"], args["height"], args["filename"]))
return args["filename"]
'''

# Every snippet, so tests can check they all compile.
ALL_SNIPPETS = {
    name: value
    for name, value in dict(globals()).items()
    if name.isupper() and isinstance(value, str) and name not in {"PRELUDE", "RESULT_MARKER"}
}
