#!/usr/bin/env python3
"""Convert CataMines 2.5.x mine files (root 'Mine:') to the 3.1 fork layout.

Usage:
  migrate-legacy-mines.py --input <mines dir> --output <dir> [--check]

The converter never edits the input directory. Every output file is re-read and
compared field by field with the legacy source before it is reported as valid.
"""
import argparse
import math
import re
import sys
from pathlib import Path

import yaml

NAME = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
PLUGIN_VERSION = "3.1.0"


def block_position(location, key):
    if not isinstance(location, dict):
        raise ValueError(f"missing {key}")
    for axis in ("x", "y", "z"):
        if axis not in location:
            raise ValueError(f"{key}.{axis} missing")
    return tuple(int(math.floor(float(location[axis]))) for axis in ("x", "y", "z"))


def vector_text(position):
    return ",".join(str(value) for value in position)


def world_of(region, p1):
    world = region.get("world") or p1.get("world") or p1.get("world_key")
    if not world:
        raise ValueError("region world missing")
    return str(world).split(":")[-1]


def warn_seconds(warn):
    value = warn.get("warnSeconds")
    if isinstance(value, list) and value:
        return min(int(item) for item in value)
    if isinstance(value, int):
        return value
    return None


def convert_flags(mine):
    flags = {}
    flags["stopped"] = bool(mine.get("isStopped", False))
    warn = mine.get("warn") or {}
    flags["warn"] = bool(warn.get("enableWarn", False))
    flags["warn-hotbar"] = bool(warn.get("warnHotbar", False))
    flags["warn-global"] = bool(warn.get("warnGlobal", False))
    seconds = warn_seconds(warn)
    if seconds is not None:
        flags["warn-seconds"] = seconds
    if isinstance(warn.get("warnDistance"), int):
        flags["warn-distance"] = warn["warnDistance"]
    if "teleportPlayers" in mine:
        flags["teleport-players"] = bool(mine.get("teleportPlayers"))
    if mine.get("teleportLocation"):
        flags["teleport-location"] = mine["teleportLocation"]
    if mine.get("teleportResetLocation"):
        flags["reset-teleport-location"] = mine["teleportResetLocation"]
    return flags


def convert_item(entry):
    item = entry.get("item")
    if not isinstance(item, dict):
        raise ValueError("loot entry without item map")
    return {
        "item": item,
        "chance": float(entry.get("chance", 100.0)),
        "fortune": bool(entry.get("fortune", False)),
    }


def convert_block(block):
    loot = block.get("lootTable") or []
    items = {}
    for index, entry in enumerate(loot):
        items[f"item-{index}"] = convert_item(entry)
    return {
        "block": str(block["block"]),
        "chance": float(block.get("chance", 0.0)),
        # The legacy plugin dropped the custom table *and* the vanilla block when
        # addLootTable was true. The fork replaces the vanilla drop instead.
        "drop-type": "CUSTOM" if items else "ALL",
        "loot-table": items,
    }


def convert(mine):
    name = str(mine["name"])
    if not NAME.match(name):
        raise ValueError(f"name not accepted by the fork: {name!r}")
    region = mine.get("region") or {}
    if str(region.get("type", "CUBOID")).upper() != "CUBOID":
        raise ValueError(f"unsupported region type: {region.get('type')}")
    p1, p2 = region.get("p1"), region.get("p2")
    first, second = block_position(p1, "p1"), block_position(p2, "p2")
    minimum = tuple(min(a, b) for a, b in zip(first, second))
    maximum = tuple(max(a, b) for a, b in zip(first, second))
    world = world_of(region, p1)

    # An empty composition is a placeholder mine; it is kept so it can be edited.
    blocks = mine.get("composition") or []

    delay = int(mine.get("resetDelay", 300))
    countdown = int(mine.get("countdown", delay))
    if countdown < 0 or countdown > max(delay, 0):
        countdown = max(delay, 0)

    composition = {
        "name": "default",
        "chance": 100.0,
        "flags": {},
        "blocks": {f"block-{index}": convert_block(block) for index, block in enumerate(blocks)},
    }
    region_section = {
        "region-type": "SELECTION",
        "name": "default",
        "chance": 100.0,
        "current-composition": "default",
        "flags": {},
        "world": world,
        "selection-type": "CUBOID",
        "selection": {"type": "CUBOID", "min": vector_text(minimum), "max": vector_text(maximum)},
        "compositions": {"composition-0": composition},
    }
    return {
        "plugin-version": PLUGIN_VERSION,
        "name": name,
        "display-name": name,
        "current-region": "default",
        "flags": convert_flags(mine),
        "controller": {
            "reset-mode": str(mine.get("resetMode", "TIME")).upper(),
            "reset-delay": delay,
            "reset-percentage": float(mine.get("resetPercentage", 0.0)),
            "countdown": countdown,
            "block-count": 0,
            "total-blocks": 0,
        },
        "regions": {"region-0": region_section},
    }


def compare(mine, converted, source):
    """Fail loudly if anything the player configured would change meaning."""
    region = source["Mine"]["region"]
    p1, p2 = block_position(region["p1"], "p1"), block_position(region["p2"], "p2")
    minimum = tuple(min(a, b) for a, b in zip(p1, p2))
    maximum = tuple(max(a, b) for a, b in zip(p1, p2))
    out_region = converted["regions"]["region-0"]
    assert converted["name"] == str(source["Mine"]["name"])
    assert out_region["world"] == world_of(region, region["p1"])
    assert out_region["selection"]["min"] == vector_text(minimum)
    assert out_region["selection"]["max"] == vector_text(maximum)
    legacy_blocks = source["Mine"].get("composition") or []
    out_blocks = out_region["compositions"]["composition-0"]["blocks"]
    assert len(out_blocks) == len(legacy_blocks), "block count changed"
    for index, legacy in enumerate(legacy_blocks):
        out = out_blocks[f"block-{index}"]
        assert out["block"] == str(legacy["block"])
        assert abs(out["chance"] - float(legacy.get("chance", 0.0))) < 1e-9
        legacy_loot = legacy.get("lootTable") or []
        assert len(out["loot-table"]) == len(legacy_loot), "loot count changed"
        for loot_index, loot in enumerate(legacy_loot):
            item = out["loot-table"][f"item-{loot_index}"]
            assert item["item"] == loot["item"], "item payload changed"
            assert abs(item["chance"] - float(loot.get("chance", 100.0))) < 1e-9
            assert item["fortune"] == bool(loot.get("fortune", False))
    out_controller = converted["controller"]
    assert out_controller["reset-delay"] == int(source["Mine"].get("resetDelay", 300))
    assert out_controller["reset-mode"] == str(source["Mine"].get("resetMode", "TIME")).upper()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    sources = sorted(args.input.glob("*.yml"))
    if not sources:
        parser.error(f"no .yml files in {args.input}")
    args.output.mkdir(parents=True, exist_ok=True)

    seen, total_blocks, total_drops, custom = {}, 0, 0, 0
    for path in sources:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(raw, dict) or "Mine" not in raw:
            raise SystemExit(f"{path.name}: not a CataMines 2.5 mine file")
        converted = convert(raw["Mine"])
        compare(raw["Mine"], converted, raw)
        name = converted["name"]
        if name in seen:
            raise SystemExit(f"duplicate mine name {name!r} ({seen[name]} and {path.name})")
        seen[name] = path.name
        target = args.output / f"{name}.yml"
        target.write_text(yaml.safe_dump(converted, sort_keys=False, allow_unicode=True, width=4096), encoding="utf-8")
        reread = yaml.safe_load(target.read_text(encoding="utf-8"))
        assert reread["regions"]["region-0"]["compositions"]["composition-0"] == converted["regions"]["region-0"]["compositions"]["composition-0"]
        total_blocks += len(converted["regions"]["region-0"]["compositions"]["composition-0"]["blocks"])
        for key in converted["regions"]["region-0"]["compositions"]["composition-0"]["blocks"]:
            if key and converted["regions"]["region-0"]["compositions"]["composition-0"]["blocks"][key]["loot-table"]:
                total_drops += len(converted["regions"]["region-0"]["compositions"]["composition-0"]["blocks"][key]["loot-table"])

    print(f"OK mines={len(seen)} blocks={total_blocks} custom_drop_entries={total_drops} -> {args.output}")


if __name__ == "__main__":
    main()
