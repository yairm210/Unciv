#!/usr/bin/env python3
"""Manage repository-backed Unciv mod imports.

This tool intentionally keeps mod source updates reviewable:
- mods-manifest.json pins every repo-backed mod to an exact commit
- update checks report one mod per prospective PR
- imports preserve upstream README/LICENSE/CREDITS files for attribution
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = REPO_ROOT / "mods-manifest.json"
DEFAULT_MODS_SRC = REPO_ROOT / "mods-src"
DEFAULT_ATTRIBUTION_DIR = REPO_ROOT / "docs" / "mod-attribution"
RECIV_RULESET_NAME = "Reciv - Vanilla"
RECIV_JSON_DIR = REPO_ROOT / "android" / "assets" / "jsons" / RECIV_RULESET_NAME
RECIV_IMAGE_DIR = REPO_ROOT / "android" / "Images.Reciv"
ANDROID_ASSETS_DIR = REPO_ROOT / "android" / "assets"
KNOWN_LICENSE_NAMES = {
    "license",
    "license.md",
    "license.txt",
    "copying",
    "copying.md",
    "copying.txt",
}
KNOWN_README_NAMES = {"readme", "readme.md", "readme.txt"}
KNOWN_CREDITS_NAMES = {"credits", "credits.md", "credits.txt", "authors", "authors.md", "authors.txt"}
KNOWN_MOD_ROOTS = {
    "jsons",
    "images",
    "maps",
    "sounds",
    "music",
    "voices",
    "fonts",
}
RULESET_ARRAY_FILES = {
    "Beliefs.json",
    "Buildings.json",
    "CityStateTypes.json",
    "Difficulties.json",
    "Eras.json",
    "Events.json",
    "Nations.json",
    "Personalities.json",
    "Quests.json",
    "Ruins.json",
    "Specialists.json",
    "Speeds.json",
    "Terrains.json",
    "TileImprovements.json",
    "TileResources.json",
    "Tutorials.json",
    "UnitNameGroups.json",
    "UnitPromotions.json",
    "Units.json",
    "UnitTypes.json",
    "VictoryTypes.json",
}
REMOVAL_FIELDS = {
    "beliefsToRemove": "Beliefs.json",
    "buildingsToRemove": "Buildings.json",
    "nationsToRemove": "Nations.json",
    "policiesToRemove": "Policies.json",
    "religionsToRemove": "Religions.json",
    "techsToRemove": "Techs.json",
    "unitsToRemove": "Units.json",
}


class ModToolError(Exception):
    pass


@dataclass(frozen=True)
class ModSource:
    name: str
    slug: str
    url: str
    ref: str
    pinned_commit: str
    include_in_reciv_vanilla: bool
    integration_role: str
    integration_order: int


def run(command: list[str], cwd: Path | None = None, capture: bool = True) -> str:
    try:
        completed = subprocess.run(
            command,
            cwd=str(cwd) if cwd else None,
            check=True,
            text=True,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE if capture else None,
        )
    except FileNotFoundError as exc:
        raise ModToolError(f"Required executable not found: {command[0]}") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        stdout = (exc.stdout or "").strip()
        detail = stderr or stdout or f"exit code {exc.returncode}"
        raise ModToolError(f"Command failed: {' '.join(command)}\n{detail}") from exc
    return (completed.stdout or "").strip()


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise ModToolError(f"Manifest not found: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ModToolError(f"Invalid JSON in {path}: {exc}") from exc


def save_manifest(path: Path, manifest: dict[str, Any]) -> None:
    path.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def mod_slug(mod: dict[str, Any]) -> str:
    source = mod.get("source", {})
    return str(mod.get("slug") or source.get("slug") or mod.get("name") or "").strip()


def parse_mods(manifest: dict[str, Any]) -> list[ModSource]:
    mods = manifest.get("mods")
    if not isinstance(mods, list):
        raise ModToolError("mods-manifest.json must contain a top-level 'mods' array")

    parsed: list[ModSource] = []
    for index, mod in enumerate(mods):
        if not isinstance(mod, dict):
            raise ModToolError(f"mods[{index}] must be an object")
        source = mod.get("source", {})
        if not isinstance(source, dict):
            raise ModToolError(f"mods[{index}].source must be an object")
        source_type = source.get("type", "repo")
        if source_type != "repo":
            continue

        name = str(mod.get("name") or "").strip()
        slug = mod_slug(mod)
        url = str(source.get("url") or "").strip()
        ref = str(source.get("ref") or "main").strip()
        pinned_commit = str(source.get("pinnedCommit") or "").strip()
        include = bool(mod.get("includeInRecivVanilla", mod.get("include_in_reciv_vanilla", True)))
        role = str(mod.get("integrationRole") or "extension").strip()
        order = int(mod.get("integrationOrder") or index * 10)
        missing = []
        if not name:
            missing.append("name")
        if not slug:
            missing.append("slug")
        if not url:
            missing.append("source.url")
        if not ref:
            missing.append("source.ref")
        if missing:
            raise ModToolError(f"mods[{index}] is missing required fields: {', '.join(missing)}")

        parsed.append(ModSource(name, slug, url, ref, pinned_commit, include, role, order))
    return parsed


def resolve_remote_ref(mod: ModSource) -> str:
    output = run(["git", "ls-remote", mod.url, mod.ref])
    if not output:
        output = run(["git", "ls-remote", mod.url, f"refs/heads/{mod.ref}"])
    if not output:
        output = run(["git", "ls-remote", mod.url, f"refs/tags/{mod.ref}"])
    if not output:
        raise ModToolError(f"Could not resolve ref '{mod.ref}' for {mod.name} ({mod.url})")
    return output.split()[0]


def discover_updates(manifest: dict[str, Any], mod_filter: str | None = None) -> list[dict[str, str]]:
    updates: list[dict[str, str]] = []
    for mod in parse_mods(manifest):
        if mod_filter and mod.slug != mod_filter and mod.name != mod_filter:
            continue
        latest = resolve_remote_ref(mod)
        if not mod.pinned_commit or mod.pinned_commit != latest:
            updates.append(
                {
                    "name": mod.name,
                    "slug": mod.slug,
                    "url": mod.url,
                    "ref": mod.ref,
                    "previous": mod.pinned_commit,
                    "latest": latest,
                }
            )
    return updates


def clone_mod(mod: ModSource, destination: Path) -> str:
    if destination.exists():
        shutil.rmtree(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    run(["git", "clone", "--depth", "1", "--branch", mod.ref, mod.url, str(destination)], capture=False)
    commit = run(["git", "rev-parse", "HEAD"], cwd=destination)
    return commit


def clone_mod_for_generation(mod: ModSource, destination: Path) -> str:
    if destination.exists():
        shutil.rmtree(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    run(["git", "clone", mod.url, str(destination)], capture=False)
    if mod.pinned_commit:
        run(["git", "checkout", "--detach", mod.pinned_commit], cwd=destination)
    else:
        print(f"WARNING: {mod.name} has no pinnedCommit; generating from ref '{mod.ref}'", file=sys.stderr)
        run(["git", "checkout", mod.ref], cwd=destination)
    return run(["git", "rev-parse", "HEAD"], cwd=destination)


def find_mod_payload_root(repo_dir: Path) -> Path:
    children = [child for child in repo_dir.iterdir() if child.name != ".git"]
    known_here = [child for child in children if child.name.lower() in KNOWN_MOD_ROOTS or child.name.lower().startswith("images")]
    if known_here:
        return repo_dir

    directories = [child for child in children if child.is_dir() and not child.name.startswith(".")]
    if len(directories) == 1:
        nested = directories[0]
        nested_known = [
            child for child in nested.iterdir()
            if child.name.lower() in KNOWN_MOD_ROOTS or child.name.lower().startswith("images")
        ]
        if nested_known:
            return nested
    raise ModToolError(
        f"Could not identify an Unciv mod root in {repo_dir}. Expected jsons, Images*, maps, sounds, music, voices, or fonts."
    )


def copy_payload(source_root: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)

    allowed_roots = set(KNOWN_MOD_ROOTS)
    for child in source_root.iterdir():
        lower_name = child.name.lower()
        is_payload = lower_name in allowed_roots or lower_name.startswith("images")
        is_metadata = lower_name in KNOWN_LICENSE_NAMES or lower_name in KNOWN_README_NAMES or lower_name in KNOWN_CREDITS_NAMES
        if not (is_payload or is_metadata):
            continue
        target = destination / child.name
        if child.is_dir():
            shutil.copytree(child, target)
        else:
            shutil.copy2(child, target)


def collect_metadata_files(mod_dir: Path) -> dict[str, list[str]]:
    result = {"licenses": [], "readmes": [], "credits": []}
    for child in mod_dir.iterdir():
        lower_name = child.name.lower()
        if lower_name in KNOWN_LICENSE_NAMES:
            result["licenses"].append(child.name)
        elif lower_name in KNOWN_README_NAMES:
            result["readmes"].append(child.name)
        elif lower_name in KNOWN_CREDITS_NAMES:
            result["credits"].append(child.name)
    return result


def write_attribution_summary(mod: ModSource, mod_dir: Path, attribution_dir: Path) -> None:
    metadata = collect_metadata_files(mod_dir)
    attribution_dir.mkdir(parents=True, exist_ok=True)
    summary = [
        f"# {mod.name}",
        "",
        f"- Source: {mod.url}",
        f"- Ref: {mod.ref}",
        f"- Pinned commit: {mod.pinned_commit or 'not pinned yet'}",
        f"- License files: {', '.join(metadata['licenses']) if metadata['licenses'] else 'none found'}",
        f"- README files: {', '.join(metadata['readmes']) if metadata['readmes'] else 'none found'}",
        f"- Credits files: {', '.join(metadata['credits']) if metadata['credits'] else 'none found'}",
        "",
        "This summary is for attribution and provenance. It is not an import gate.",
        "",
    ]
    (attribution_dir / f"{mod.slug}.md").write_text("\n".join(summary), encoding="utf-8")


def update_manifest_pin(manifest: dict[str, Any], slug: str, commit: str, metadata: dict[str, list[str]]) -> None:
    for mod in manifest.get("mods", []):
        if mod_slug(mod) != slug:
            continue
        source = mod.setdefault("source", {})
        source["pinnedCommit"] = commit
        attribution_info = mod.setdefault("attribution", {})
        attribution_info["licenseFiles"] = metadata["licenses"]
        attribution_info["readmes"] = metadata["readmes"]
        attribution_info["credits"] = metadata["credits"]
        return
    raise ModToolError(f"Mod not found in manifest: {slug}")


def import_mod(manifest_path: Path, slug: str, dry_run: bool) -> None:
    manifest = load_manifest(manifest_path)
    mods = {mod.slug: mod for mod in parse_mods(manifest)}
    mod = mods.get(slug)
    if mod is None:
        raise ModToolError(f"Mod not found in manifest: {slug}")

    with tempfile.TemporaryDirectory(prefix=f"reciv-{mod.slug}-") as tmp:
        clone_dir = Path(tmp) / "repo"
        commit = clone_mod(mod, clone_dir)
        payload_root = find_mod_payload_root(clone_dir)
        destination = DEFAULT_MODS_SRC / mod.slug
        if dry_run:
            metadata = collect_metadata_files(payload_root)
            print(json.dumps({"mod": mod.slug, "commit": commit, "payloadRoot": str(payload_root), "metadata": metadata}, indent=2))
            return

        copy_payload(payload_root, destination)
        metadata = collect_metadata_files(destination)
        updated_mod = ModSource(
            mod.name, mod.slug, mod.url, mod.ref, commit,
            mod.include_in_reciv_vanilla, mod.integration_role, mod.integration_order
        )
        write_attribution_summary(updated_mod, destination, DEFAULT_ATTRIBUTION_DIR)
        update_manifest_pin(manifest, mod.slug, commit, metadata)
        save_manifest(manifest_path, manifest)


def strip_json_comments(text: str) -> str:
    result: list[str] = []
    in_string = False
    escaped = False
    index = 0
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""

        if in_string:
            result.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue

        if char == '"':
            in_string = True
            result.append(char)
            index += 1
        elif char == "/" and next_char == "/":
            while index < len(text) and text[index] not in "\r\n":
                index += 1
        elif char == "/" and next_char == "*":
            index += 2
            while index + 1 < len(text) and not (text[index] == "*" and text[index + 1] == "/"):
                index += 1
            index += 2
        else:
            result.append(char)
            index += 1

    return "".join(result)


def strip_trailing_json_commas(text: str) -> str:
    result: list[str] = []
    in_string = False
    escaped = False
    index = 0
    while index < len(text):
        char = text[index]

        if in_string:
            result.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue

        if char == '"':
            in_string = True
            result.append(char)
            index += 1
            continue

        if char == ",":
            lookahead = index + 1
            while lookahead < len(text) and text[lookahead].isspace():
                lookahead += 1
            if lookahead < len(text) and text[lookahead] in "}]":
                index += 1
                continue

        result.append(char)
        index += 1

    return "".join(result)


def insert_missing_json_value_commas(text: str) -> str:
    result: list[str] = []
    in_string = False
    escaped = False
    index = 0
    while index < len(text):
        char = text[index]

        if in_string:
            result.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            index += 1
            continue

        if char == '"':
            in_string = True
            result.append(char)
            index += 1
            continue

        if char in "}]":
            lookahead = index + 1
            while lookahead < len(text) and text[lookahead].isspace():
                lookahead += 1
            if lookahead < len(text) and text[lookahead] in "{[":
                result.append(char)
                result.append(",")
                index += 1
                continue

        result.append(char)
        index += 1

    return "".join(result)


def load_json_file(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    text = path.read_text(encoding="utf-8-sig")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        lenient_text = strip_json_comments(text)
        lenient_text = insert_missing_json_value_commas(lenient_text)
        lenient_text = strip_trailing_json_commas(lenient_text)
        try:
            return json.loads(lenient_text)
        except json.JSONDecodeError as exc:
            raise ModToolError(f"Invalid JSON in {path}: {exc}") from exc


def write_json_file(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=4, ensure_ascii=False) + "\n", encoding="utf-8")


def object_name(item: dict[str, Any]) -> str | None:
    value = item.get("name")
    return str(value) if value is not None else None


def remove_named_items(items: list[Any], removals: set[str]) -> list[Any]:
    if not removals:
        return items
    return [
        item for item in items
        if not isinstance(item, dict) or object_name(item) not in removals
    ]


def merge_named_array(current: list[Any], incoming: list[Any]) -> list[Any]:
    result = list(current)
    indexes = {
        object_name(item): index
        for index, item in enumerate(result)
        if isinstance(item, dict) and object_name(item) is not None
    }
    for item in incoming:
        if not isinstance(item, dict):
            if item not in result:
                result.append(item)
            continue
        name = object_name(item)
        if name is None:
            result.append(item)
        elif name in indexes:
            result[indexes[name]] = item
        else:
            indexes[name] = len(result)
            result.append(item)
    return result


def merge_religions(current: list[str], incoming: list[str], removals: set[str]) -> list[str]:
    result = [religion for religion in current if religion not in removals]
    for religion in incoming:
        if religion not in result:
            result.append(religion)
    return result


def merge_global_uniques(current: dict[str, Any], incoming: dict[str, Any]) -> dict[str, Any]:
    result = dict(current)
    result.update({key: value for key, value in incoming.items() if key != "uniques"})
    uniques = list(result.get("uniques") or [])
    for unique in incoming.get("uniques") or []:
        if unique not in uniques:
            uniques.append(unique)
    if uniques:
        result["uniques"] = uniques
    return result


def merge_mod_options(current: dict[str, Any], incoming: dict[str, Any], base_name: str) -> dict[str, Any]:
    result = dict(current)
    for key, value in incoming.items():
        if key.endswith("ToRemove"):
            continue
        if key == "uniques" and isinstance(value, list):
            uniques = list(result.get("uniques") or [])
            for unique in value:
                if unique not in uniques:
                    uniques.append(unique)
            result["uniques"] = uniques
        elif key == "constants" and isinstance(value, dict):
            constants = dict(result.get("constants") or {})
            constants.update(value)
            result["constants"] = constants
        else:
            result[key] = value
    result["isBaseRuleset"] = True
    result["name"] = "ModOptions"
    result["modUrl"] = "https://github.com/hnew1/Reciv"
    result["author"] = "Reciv"
    result["uniques"] = list(dict.fromkeys(result.get("uniques") or []))
    result["sourceBaseRuleset"] = base_name
    return result


def merge_techs(current: list[dict[str, Any]], incoming: list[dict[str, Any]], removals: set[str]) -> list[dict[str, Any]]:
    result = []
    tech_to_column: dict[str, tuple[int, int]] = {}
    for column in current:
        new_column = dict(column)
        techs = [
            tech for tech in list(new_column.get("techs") or [])
            if isinstance(tech, dict) and tech.get("name") not in removals
        ]
        new_column["techs"] = techs
        for tech_index, tech in enumerate(techs):
            if isinstance(tech, dict) and tech.get("name") is not None:
                tech_to_column[str(tech["name"])] = (len(result), tech_index)
        result.append(new_column)

    def matching_column_index(incoming_column: dict[str, Any]) -> int | None:
        for key in ("columnNumber", "techCost", "era"):
            if key not in incoming_column:
                continue
            for index, existing in enumerate(result):
                if existing.get(key) == incoming_column.get(key):
                    return index
        return None

    for incoming_column in incoming:
        target_index = matching_column_index(incoming_column)
        if target_index is None:
            new_column = dict(incoming_column)
            new_column["techs"] = list(new_column.get("techs") or [])
            result.append(new_column)
            target_index = len(result) - 1

        for tech in incoming_column.get("techs") or []:
            if not isinstance(tech, dict) or tech.get("name") is None:
                continue
            tech_name = str(tech["name"])
            if tech_name in tech_to_column:
                column_index, tech_index = tech_to_column[tech_name]
                result[column_index]["techs"][tech_index] = tech
            else:
                tech_to_column[tech_name] = (target_index, len(result[target_index].setdefault("techs", [])))
                result[target_index].setdefault("techs", []).append(tech)
    return result


def merge_policies(current: list[dict[str, Any]], incoming: list[dict[str, Any]], mod_options: dict[str, Any]) -> list[dict[str, Any]]:
    branches_to_remove = set(mod_options.get("policyBranchesToRemove") or [])
    policies_to_remove = set(mod_options.get("policiesToRemove") or [])
    current = [
        branch for branch in current
        if isinstance(branch, dict) and branch.get("name") not in branches_to_remove
    ]
    for branch in current:
        branch["policies"] = [
            policy for policy in branch.get("policies") or []
            if not isinstance(policy, dict) or policy.get("name") not in policies_to_remove
        ]
    return merge_named_array(current, incoming)


def apply_mod_json(generated: dict[str, Any], mod_json_dir: Path, mod_name: str) -> None:
    mod_options = load_json_file(mod_json_dir / "ModOptions.json", {})

    for removal_field, filename in REMOVAL_FIELDS.items():
        removals = set(mod_options.get(removal_field) or [])
        if removals and filename in generated and filename not in {"Religions.json", "Techs.json", "Policies.json"}:
            generated[filename] = remove_named_items(generated[filename], removals)

    for filename in RULESET_ARRAY_FILES:
        incoming_path = mod_json_dir / filename
        if not incoming_path.exists():
            continue
        generated[filename] = merge_named_array(generated.get(filename, []), load_json_file(incoming_path, []))

    if (mod_json_dir / "Religions.json").exists() or mod_options.get("religionsToRemove"):
        generated["Religions.json"] = merge_religions(
            generated.get("Religions.json", []),
            load_json_file(mod_json_dir / "Religions.json", []),
            set(mod_options.get("religionsToRemove") or [])
        )

    if (mod_json_dir / "Policies.json").exists() or mod_options.get("policiesToRemove") or mod_options.get("policyBranchesToRemove"):
        generated["Policies.json"] = merge_policies(
            generated.get("Policies.json", []),
            load_json_file(mod_json_dir / "Policies.json", []),
            mod_options
        )

    if (mod_json_dir / "Techs.json").exists() or mod_options.get("techsToRemove"):
        generated["Techs.json"] = merge_techs(
            generated.get("Techs.json", []),
            load_json_file(mod_json_dir / "Techs.json", []),
            set(mod_options.get("techsToRemove") or [])
        )

    if (mod_json_dir / "GlobalUniques.json").exists():
        generated["GlobalUniques.json"] = merge_global_uniques(
            generated.get("GlobalUniques.json", {}),
            load_json_file(mod_json_dir / "GlobalUniques.json", {})
        )

    generated["ModOptions.json"] = merge_mod_options(
        generated.get("ModOptions.json", {}),
        mod_options,
        mod_name
    )


def copy_tree_contents(source: Path, destination: Path) -> None:
    if not source.exists():
        return
    for item in source.iterdir():
        target = destination / item.name
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)


def copy_generated_media(mod_dir: Path) -> None:
    for item in mod_dir.iterdir():
        lower = item.name.lower()
        if item.is_dir() and lower.startswith("images"):
            copy_tree_contents(item, RECIV_IMAGE_DIR)
        elif item.is_dir() and lower in {"sounds", "music"}:
            copy_tree_contents(item, ANDROID_ASSETS_DIR / lower)
        elif item.is_dir() and lower in {"fonts", "maps"}:
            copy_tree_contents(item, ANDROID_ASSETS_DIR / lower)


def clean_generated_outputs() -> None:
    for path in [RECIV_JSON_DIR, RECIV_IMAGE_DIR]:
        if path.exists():
            shutil.rmtree(path)


def ensure_mod_imported(manifest_path: Path, manifest: dict[str, Any], mod: ModSource, refresh_sources: bool) -> None:
    destination = DEFAULT_MODS_SRC / mod.slug
    if refresh_sources or not destination.exists():
        with tempfile.TemporaryDirectory(prefix=f"reciv-{mod.slug}-") as tmp:
            clone_dir = Path(tmp) / "repo"
            commit = clone_mod_for_generation(mod, clone_dir)
            payload_root = find_mod_payload_root(clone_dir)
            copy_payload(payload_root, destination)
            metadata = collect_metadata_files(destination)
            updated_mod = ModSource(
                mod.name, mod.slug, mod.url, mod.ref, commit,
                mod.include_in_reciv_vanilla, mod.integration_role, mod.integration_order
            )
            write_attribution_summary(updated_mod, destination, DEFAULT_ATTRIBUTION_DIR)


def generate_reciv_vanilla(manifest_path: Path, refresh_sources: bool) -> None:
    manifest = load_manifest(manifest_path)
    mods = sorted(
        [mod for mod in parse_mods(manifest) if mod.include_in_reciv_vanilla],
        key=lambda mod: mod.integration_order
    )
    if not mods:
        raise ModToolError("No mods are marked includeInRecivVanilla=true")

    generated: dict[str, Any] = {}
    clean_generated_outputs()

    for mod in mods:
        ensure_mod_imported(manifest_path, manifest, mod, refresh_sources)
        mod_dir = DEFAULT_MODS_SRC / mod.slug
        mod_json_dir = mod_dir / "jsons"
        if not mod_json_dir.exists():
            raise ModToolError(f"Imported mod has no jsons folder: {mod.slug}")
        apply_mod_json(generated, mod_json_dir, mod.name)
        copy_generated_media(mod_dir)

    generated["ModOptions.json"] = merge_mod_options(
        generated.get("ModOptions.json", {}),
        {"isBaseRuleset": True},
        RECIV_RULESET_NAME
    )
    generated["ModOptions.json"]["displayName"] = RECIV_RULESET_NAME
    generated["ModOptions.json"]["sourceMods"] = [mod.name for mod in mods]

    for filename, data in sorted(generated.items()):
        write_json_file(RECIV_JSON_DIR / filename, data)

    print(f"Generated {RECIV_RULESET_NAME} from {len(mods)} mods at {RECIV_JSON_DIR}")


def validate_manifest(manifest_path: Path) -> None:
    manifest = load_manifest(manifest_path)
    parse_mods(manifest)
    print(f"Manifest is valid: {manifest_path}")


def write_github_matrix(updates: list[dict[str, str]]) -> None:
    matrix = {"include": [{"slug": update["slug"], "name": update["name"]} for update in updates]}
    print(f"matrix={json.dumps(matrix, separators=(',', ':'))}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("validate-manifest")

    check = subparsers.add_parser("check-updates")
    check.add_argument("--mod")
    check.add_argument("--github-matrix", action="store_true")

    update_one = subparsers.add_parser("update-one")
    update_one.add_argument("--mod", required=True)
    update_one.add_argument("--dry-run", action="store_true")

    generate = subparsers.add_parser("generate-reciv-vanilla")
    generate.add_argument("--no-refresh-sources", action="store_true")

    args = parser.parse_args(argv)

    try:
        if args.command == "validate-manifest":
            validate_manifest(args.manifest)
        elif args.command == "check-updates":
            updates = discover_updates(load_manifest(args.manifest), args.mod)
            if args.github_matrix:
                write_github_matrix(updates)
            else:
                print(json.dumps(updates, indent=2))
        elif args.command == "update-one":
            import_mod(args.manifest, args.mod, args.dry_run)
        elif args.command == "generate-reciv-vanilla":
            generate_reciv_vanilla(args.manifest, not args.no_refresh_sources)
        else:
            raise ModToolError(f"Unknown command: {args.command}")
    except ModToolError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error::{str(exc).replace(chr(10), '%0A')}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
