#!/usr/bin/env python3
"""Resolve the MC × fabric-api version matrix into versions.json.

Sources:
  - piston-meta (Mojang): list of release Minecraft versions (>= min_minecraft)
  - Modrinth: for each MC version, the newest fabric-api release.

Modes:
  --update   fetch upstream and rewrite versions.json
  --check    compare versions.json against upstream; exit 1 if stale
  --matrix   read versions.json and print the GitHub Actions strategy matrix
             JSON to stdout

No third-party deps (stdlib urllib only), so CI needs no pip install.
"""

import argparse
import json
import os
import sys
import urllib.request
from datetime import datetime, timezone

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSIONS_FILE = os.path.join(ROOT_DIR, "versions.json")

PISTON_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
MODRINTH_API = "https://api.modrinth.com/v2"
FABRIC_API_SLUG = "fabric-api"

MIN_MINECRAFT = "26.1"
USER_AGENT = "yuanlu-warehouse/matrix-resolver (github.com/MineYuanlu/mc-warehouse)"


def http_get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode("utf-8")


def modrinth_versions(slug: str, game_version: str) -> "list[dict]":
    url = (
        f"{MODRINTH_API}/project/{slug}/version?"
        f"loaders=%5B%22fabric%22%5D&game_versions=%5B%22{game_version}%22%5D"
    )
    return json.loads(http_get(url))


def mc_releases() -> "list[str]":
    manifest = json.loads(http_get(PISTON_URL))
    releases = [v["id"] for v in manifest["versions"] if v["type"] == "release"]
    return [v for v in releases if v >= MIN_MINECRAFT]


def pick_fabric_api(game_version: str) -> "str | None":
    versions = [v for v in modrinth_versions(FABRIC_API_SLUG, game_version)
                if v["version_type"] == "release"]
    if not versions:
        return None
    versions.sort(key=lambda v: v["date_published"])
    return versions[-1]["version_number"]


def resolve() -> "list[dict]":
    rows = []
    for mc in mc_releases():
        fabric_api = pick_fabric_api(mc)
        if fabric_api is None:
            print(f"  WARN {mc}: no release fabric-api build found, skipped")
            continue
        rows.append({
            "id": mc,
            "java": 25,
            "fabricApi": fabric_api,
        })
    return rows


def load_committed() -> "dict":
    with open(VERSIONS_FILE) as f:
        return json.load(f)


def canonical(rows: "list[dict]") -> "dict":
    return {
        "schema": 1,
        "generated": datetime.now(timezone.utc).replace(microsecond=0)
        .isoformat().replace("+00:00", "Z"),
        "minMinecraft": MIN_MINECRAFT,
        "versions": rows,
    }


def write_versions(rows: "list[dict]") -> None:
    with open(VERSIONS_FILE, "w") as f:
        json.dump(canonical(rows), f, indent=2)
        f.write("\n")


def print_matrix(rows: "list[dict]") -> None:
    """Emit the GitHub Actions strategy matrix JSON (one row per MC version)."""
    include = [{
        "mc": r["id"],
        "java": r["java"],
        "fabricApi": r["fabricApi"],
    } for r in rows]
    print(json.dumps({"include": include}, indent=2))


def cmd_update(_args) -> int:
    print("Resolving version matrix from upstream ...")
    rows = resolve()
    write_versions(rows)
    print(f"  wrote {VERSIONS_FILE}: {len(rows)} MC rows")
    for r in rows:
        print(f"    {r['id']:6s} fabricApi={r['fabricApi']}")
    return 0


def cmd_check(_args) -> int:
    live = resolve()
    committed = load_committed()
    live_canonical = canonical(live)["versions"]
    committed_versions = committed.get("versions", [])
    if live_canonical == committed_versions:
        print("versions.json is up to date")
        return 0
    print("versions.json is STALE:")
    for lv in live_canonical:
        cv = next((c for c in committed_versions if c["id"] == lv["id"]), None)
        if cv != lv:
            print(f"  {lv['id']}: committed={cv}  live={lv}")
    for cv in committed_versions:
        if not any(l["id"] == cv["id"] for l in live_canonical):
            print(f"  {cv['id']}: committed but no longer released (obsolete)")
    return 1


def cmd_matrix(_args) -> int:
    committed = load_committed()
    print_matrix(committed["versions"])
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["update", "check", "matrix"],
                        help="operation to run")
    args = parser.parse_args()
    return {"update": cmd_update, "check": cmd_check, "matrix": cmd_matrix}[args.mode](args)


if __name__ == "__main__":
    sys.exit(main())
