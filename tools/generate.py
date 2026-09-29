#!/usr/bin/env python3
"""Generate per-Minecraft-version project directories from the canonical source."""

import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preprocess import preprocess_text  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "versions.json"), encoding="utf-8"))
ROOT = CFG["root"]
CANON = os.path.join(HERE, "canonical")

BUILD_FILES = ["build.gradle.kts", "settings.gradle.kts", ".gitignore", "LICENSE", "README.md"]
COPY_DIRS = ["gradle"]


def parse_mc(mc):
    return tuple(int(x) for x in mc.split("."))


def eval_rule(rule, mc):
    m = re.match(r"mc\s*(>=|<=|<|>|==)\s*([0-9.]+)", rule.strip())
    if not m:
        raise ValueError(f"bad rule: {rule}")
    op, val = m.group(1), parse_mc(m.group(2))
    cur = parse_mc(mc)
    # pad for tuple compare
    n = max(len(cur), len(val))
    cur = cur + (0,) * (n - len(cur))
    val = val + (0,) * (n - len(val))
    return {
        ">=": cur >= val,
        "<=": cur <= val,
        "<": cur < val,
        ">": cur > val,
        "==": cur == val,
    }[op]


def flags_for(mc):
    return {name: eval_rule(rule, mc) for name, rule in CFG["flags"].items()}


def next_version(mc):
    parts = [int(x) for x in mc.split(".")]
    parts[-1] += 1
    return ".".join(str(p) for p in parts)


def write_gradle_properties(path, mc, info, flags):
    lines = [
        "# Done to increase the memory available to gradle.",
        "org.gradle.jvmargs=-Xmx2G",
        "# Fabric Properties",
        f"minecraft_version={mc}",
        f"loader_version={info.get('loader', '0.19.3')}",
        "# Mod Properties",
        "mod_version=2.4.2",
        "maven_group=com.kevin",
        "archives_base_name=AxotiersTiertagger",
        "# Dependencies",
        f"ukulib_version={info['ukulib']}",
        f"ukulib_artifact={info['ukulib_artifact']}",
        f"fabric_api_version={info['fabric_api']}",
        "devauth_version=1.2.2",
        f"java_target={info['java']}",
        f"norisk_profile=Fabric {mc}",
        "",
    ]
    modules = []
    if not flags["ukulib_keybind"]:
        modules += ["fabric-key-binding-api-v1", "fabric-lifecycle-events-v1"]
    if flags["resource_loader"]:
        # ukulib 1.0.0-beta.3 (1.20.1 only) implements fabric's SimpleSynchronousResourceReloadListener
        modules.append("fabric-resource-loader-v0")
    if modules:
        lines.insert(-1, "extra_fabric_modules=" + ",".join(modules))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def patch_fabric_mod_json(path, mc, info):
    with open(path, "r", encoding="utf-8") as f:
        txt = f.read()
    # exact version: fabric-loader's depends predicates do not match interval
    # syntax like "[26.2,26.3)" against the plain game version (incompatible-mods error)
    txt = re.sub(r'"minecraft":\s*"[^"]*"', f'"minecraft": "{mc}"', txt)
    base = info["ukulib"].split("+")[0]
    txt = re.sub(r'"ukulib":\s*"[^"]*"', f'"ukulib": ">={base}"', txt)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(txt)


def generate(mc, info):
    flags = flags_for(mc)
    target = os.path.join(ROOT, f"TierTagger-{mc}")
    os.makedirs(target, exist_ok=True)

    for name in BUILD_FILES:
        s = os.path.join(CANON, name)
        if not os.path.exists(s):
            continue
        d = os.path.join(target, name)
        if name.endswith(".kts"):
            with open(s, "r", encoding="utf-8") as f:
                text = f.read()
            res = preprocess_text(text, flags, path=name)
            if res is None:
                if os.path.exists(d):
                    os.remove(d)
                continue
            with open(d, "w", encoding="utf-8", newline="\n") as f:
                f.write(res)
        else:
            shutil.copy2(s, d)
    for d in COPY_DIRS:
        s = os.path.join(CANON, d)
        if os.path.isdir(s):
            shutil.copytree(s, os.path.join(target, d), dirs_exist_ok=True)
    for wrapper in ("gradlew", "gradlew.bat"):
        s = os.path.join(CANON, wrapper)
        if os.path.exists(s):
            shutil.copy2(s, os.path.join(target, wrapper))

    write_gradle_properties(os.path.join(target, "gradle.properties"), mc, info, flags)

    # java sources (preprocessed)
    src_root = os.path.join(CANON, "src", "main", "java")
    out_root = os.path.join(target, "src", "main", "java")
    kept = dropped = 0
    for dirpath, _dirnames, filenames in os.walk(src_root):
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            s = os.path.join(dirpath, fn)
            rel = os.path.relpath(s, src_root)
            d = os.path.join(out_root, rel)
            with open(s, "r", encoding="utf-8") as f:
                text = f.read()
            res = preprocess_text(text, flags, path=rel)
            if res is None:
                if os.path.exists(d):
                    os.remove(d)
                dropped += 1
                continue
            os.makedirs(os.path.dirname(d), exist_ok=True)
            with open(d, "w", encoding="utf-8", newline="\n") as f:
                f.write(res)
            kept += 1

    # resources (copy everything, preprocess .json markers, patch fabric.mod.json)
    res_src = os.path.join(CANON, "src", "main", "resources")
    res_dst = os.path.join(target, "src", "main", "resources")
    shutil.copytree(res_src, res_dst, dirs_exist_ok=True)
    for dirpath, _dirnames, filenames in os.walk(res_dst):
        for fn in filenames:
            if not fn.endswith(".json"):
                continue
            fp = os.path.join(dirpath, fn)
            with open(fp, "r", encoding="utf-8") as f:
                text = f.read()
            res = preprocess_text(text, flags, path=os.path.relpath(fp, res_dst))
            if res is None:
                os.remove(fp)
            else:
                with open(fp, "w", encoding="utf-8", newline="\n") as f:
                    f.write(res)
    patch_fabric_mod_json(os.path.join(res_dst, "fabric.mod.json"), mc, info)

    print(f"{mc:>8}: {kept} files (dropped {dropped}) -> {target}")


def main():
    only = sys.argv[1:] or None
    for mc, info in CFG["versions"].items():
        if only and mc not in only:
            continue
        generate(mc, info)


if __name__ == "__main__":
    main()
