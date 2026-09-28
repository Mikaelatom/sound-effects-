#!/usr/bin/env python3
"""List every asset inside the Tensura: Reincarnated jar as a Markdown catalog.

Usage: python3 tools/catalog_tensura_assets.py <tensura.jar> [output.md]
(or run `./gradlew catalogTensuraAssets`, which finds the jar for you)

The catalog is what we pick from when building new skills: skill names and icons, particle textures,
sounds, GeckoLib models/animations and entity textures.
"""
import json
import sys
import zipfile
from collections import defaultdict


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    jar_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "tensura-assets.md"

    with zipfile.ZipFile(jar_path) as jar:
        names = sorted(n for n in jar.namelist() if n.startswith("assets/") and not n.endswith("/"))
        lang = {}
        for candidate in ("assets/tensura/lang/en_us.json",):
            if candidate in jar.namelist():
                lang = json.loads(jar.read(candidate).decode("utf-8"))
        sounds = {}
        if "assets/tensura/sounds.json" in jar.namelist():
            sounds = json.loads(jar.read("assets/tensura/sounds.json").decode("utf-8"))

    groups = defaultdict(list)
    for name in names:
        parts = name.split("/")
        # assets/<namespace>/<kind>/<sub>/...
        kind = "/".join(parts[2:4]) if len(parts) > 4 else parts[2]
        groups[f"{parts[1]}: {kind}"].append("/".join(parts[1:]))

    lines = [f"# Tensura: Reincarnated asset catalog", "", f"Source: `{jar_path.split('/')[-1]}`", ""]

    skill_names = {k: v for k, v in lang.items() if ".skill." in k or k.startswith("tensura.skill")}
    if skill_names:
        lines += ["## Skill names (from lang)", "", "| key | name |", "|---|---|"]
        lines += [f"| `{k}` | {v} |" for k, v in sorted(skill_names.items())]
        lines.append("")

    race_names = {k: v for k, v in lang.items() if ".race." in k}
    if race_names:
        lines += ["## Race names (from lang)", "", "| key | name |", "|---|---|"]
        lines += [f"| `{k}` | {v} |" for k, v in sorted(race_names.items())]
        lines.append("")

    if sounds:
        lines += ["## Sound events", ""]
        lines += [f"- `tensura:{k}`" for k in sorted(sounds)]
        lines.append("")

    lines += ["## Files by folder", ""]
    for group in sorted(groups):
        files = groups[group]
        lines += [f"### {group} ({len(files)})", ""]
        lines += [f"- `{f}`" for f in files]
        lines.append("")

    with open(out_path, "w", encoding="utf-8") as out:
        out.write("\n".join(lines))
    print(f"Wrote {out_path}: {len(names)} assets, {len(skill_names)} skill names, {len(sounds)} sounds")


if __name__ == "__main__":
    main()
