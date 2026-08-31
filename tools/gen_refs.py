#!/usr/bin/env python3
"""为依赖生成本地参考源码，仅供本地阅读。

模式：
  discover                    列出 Gradle 缓存（编译 classpath 的超集）+ Loom MC
                              缓存中的所有 jar，格式为 group:artifact:version + 绝对路径。
                              精确的编译 classpath 请用：
                              ./gradlew dependencies --configuration clientCompileClasspath
  mc                          全量反编译 Minecraft（common + clientOnly 两个原 jar）
                              到 refs/dep-src/minecraft/
  dep group:artifact:version  提取单个依赖的源码到 refs/dep-src/<artifact>/：
                              优先用 Gradle 缓存里的 -sources.jar 直接解压，
                              没有则用 fernflower 反编译

MC 26.1+ 无混淆，反编译出的类名/方法名即真名。

无第三方依赖（仅标准库）。产物已被 gitignore（见 refs/.gitignore）。
"""

import argparse
import glob
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOOM_CACHE = os.path.join(ROOT_DIR, ".gradle", "loom-cache")
GRADLE_CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
FERNFLOWER = os.path.join(ROOT_DIR, "refs", "tools", "fernflower.jar")
FERNFLOWER_URL = "https://jitpack.io/com/github/JetBrains/fernflower/master/fernflower-master.jar"
DST = os.path.join(ROOT_DIR, "refs", "dep-src")


def ensure_fernflower() -> str:
    """确保 fernflower.jar 存在，缺失则自动下载"""
    if os.path.isfile(FERNFLOWER):
        return FERNFLOWER
    os.makedirs(os.path.dirname(FERNFLOWER), exist_ok=True)
    print(f"未找到 fernflower.jar，从 {FERNFLOWER_URL} 下载…")
    urllib.request.urlretrieve(FERNFLOWER_URL, FERNFLOWER)
    if not os.path.isfile(FERNFLOWER):
        sys.exit(f"下载失败：{FERNFLOWER}")
    print(f"[ok] 已下载 FernFlower 反编译器到 {FERNFLOWER}")
    return FERNFLOWER


def decompile_with_fernflower(jar_paths: "list[str]", work_dir: str) -> "list[str]":
    """对一批 jar 执行 fernflower，返回写入 work_dir 的反编译产物 jar 路径。

    fernflower 会为每个输入 jar 生成 <dst>/<jar名> 一个输出 jar，
    并对所有输入做引用解析（跨 jar 的类引用能正确还原）。

    注意：不要传入用 Python zipfile 合并出的 jar —— fernflower 的惰性加载
    会报 "zip END header not found"（原始 Mojang jar 没这个问题）。
    把原始 jar 一起传给它即可。"""
    ensure_fernflower()
    subprocess.run(
        ["java", "-Xmx2G", "-jar", FERNFLOWER, "-dgs=true", *jar_paths, work_dir],
        check=True,
    )
    return [os.path.join(work_dir, f) for f in os.listdir(work_dir) if f.endswith(".jar")]


def extract_jar(jar_path: str, out_dir: str) -> None:
    """把 jar 解压到 out_dir。"""
    with zipfile.ZipFile(jar_path) as zf:
        zf.extractall(out_dir)


def gradle_cache_jars(group: str, artifact: str, version: str) -> "tuple[str | None, str | None]":
    """从 Gradle 缓存查找依赖的 (sources_jar, normal_jar)，找不到则对应为 None。

    缓存结构：files-2.1/<group>/<artifact>/<version>/<hash>/*.jar，
    其中 <group> 是平铺的带点目录（如 'net.fabricmc'）。"""
    pattern = os.path.join(
        GRADLE_CACHE, group, artifact, version, "*",
        f"{artifact}-{version}*.jar",
    )
    sources = normal = None
    for f in glob.glob(pattern):
        if f.endswith("-sources.jar"):
            sources = f
        elif not f.endswith("-sources.jar"):
            normal = f
    return sources, normal


def cmd_discover(_args) -> int:
    """列出 Gradle 缓存 + Loom MC 缓存中的所有 jar。"""
    print("# Gradle cache jars (~/.gradle/caches/modules-2/files-2.1)")
    cache = GRADLE_CACHE
    if os.path.isdir(cache):
        for group in sorted(os.listdir(cache)):
            gpath = os.path.join(cache, group)
            if not os.path.isdir(gpath):
                continue
            for artifact in sorted(os.listdir(gpath)):
                apath = os.path.join(gpath, artifact)
                if not os.path.isdir(apath):
                    continue
                for version in sorted(os.listdir(apath)):
                    vpath = os.path.join(apath, version)
                    for root, _dirs, files in os.walk(vpath):
                        for f in sorted(files):
                            if f.endswith(".jar"):
                                print(f"{group}:{artifact}:{version}\t{os.path.join(root, f)}")
    print("# Minecraft jars (.gradle/loom-cache/minecraftMaven)")
    for jar in sorted(glob.glob(os.path.join(
            LOOM_CACHE, "minecraftMaven", "net", "minecraft", "*", "*", "*.jar"))):
        print(f"minecraft-jar\t{jar}")
    return 0


def cmd_mc(args) -> int:
    """全量反编译 Minecraft（common + clientOnly）到 refs/dep-src/minecraft/。

    两个 jar 一起传给 fernflower：它既反编译全部类，又能跨 jar 解析引用。
    输出目录非空且未带 --force 时直接跳过（幂等）。"""
    out_dir = os.path.join(DST, "minecraft")
    if os.path.isdir(out_dir) and os.listdir(out_dir) and not args.force:
        print(f"已存在 {out_dir}（非空），跳过。用 --force 重新反编译。")
        return 0
    common = sorted(glob.glob(os.path.join(
            LOOM_CACHE, "minecraftMaven", "net", "minecraft",
            "minecraft-common-*", "*", "minecraft-common-*.jar")))
    client = sorted(glob.glob(os.path.join(
            LOOM_CACHE, "minecraftMaven", "net", "minecraft",
            "minecraft-clientOnly-*", "*", "minecraft-clientOnly-*.jar")))
    if not common or not client:
        sys.exit(f"未在 {LOOM_CACHE} 找到 common/clientOnly MC jar。"
                 "先跑一次 ./gradlew build 生成 loom 缓存。")
    print(f"反编译 MC jar（可能需要几分钟）：\n  {common[0]}\n  {client[0]}")
    try:
        with tempfile.TemporaryDirectory() as tmp:
            outputs = decompile_with_fernflower([common[0], client[0]], tmp)
            if not outputs:
                sys.exit("fernflower 无输出")
            os.makedirs(out_dir, exist_ok=True)
            for out in outputs:
                extract_jar(out, out_dir)
    except subprocess.CalledProcessError:
        sys.exit("fernflower 反编译失败")
    print(f"[ok] MC 反编译完成 → {out_dir}")
    return 0


def cmd_dep(args) -> int:
    """提取单个依赖源码到 refs/dep-src/<artifact>/。

    优先解压 -sources.jar（原味源码）；否则用 fernflower 反编译普通 jar。
    输出目录非空且未带 --force 时直接跳过（幂等）。"""
    parts = args.coord.split(":")
    if len(parts) != 3:
        sys.exit(f"依赖格式应为 'group:artifact:version'，实际为 {args.coord}")
    group, artifact, version = parts
    out_dir = os.path.join(DST, artifact)
    if os.path.isdir(out_dir) and os.listdir(out_dir) and not args.force:
        print(f"已存在 {out_dir}（非空），跳过。用 --force 重新生成。")
        return 0
    sources, normal = gradle_cache_jars(group, artifact, version)
    if sources is not None:
        os.makedirs(out_dir, exist_ok=True)
        extract_jar(sources, out_dir)
        print(f"[ok] 已从 {sources} 解压源码到 {out_dir}")
        return 0
    if normal is None:
        sys.exit(f"Gradle 缓存中未找到 {args.coord}（{GRADLE_CACHE}）。"
                 "先跑一次 build 下载依赖，或用 discover 确认坐标。")
    try:
        with tempfile.TemporaryDirectory() as tmp:
            outputs = decompile_with_fernflower([normal], tmp)
            if len(outputs) != 1:
                sys.exit(f"fernflower 输出异常：{outputs}")
            os.makedirs(out_dir, exist_ok=True)
            extract_jar(outputs[0], out_dir)
    finally:
        pass
    print(f"[ok] 已反编译 {normal} → {out_dir}")
    return 0


def cmd_file(args) -> int:
    """反编译不在 Gradle 缓存中的任意 jar（如手动下载的 Modrinth jar）。

    用法：file <jar路径> [artifact名]
    artifact 名缺省取 jar 文件名去掉版本号后的主干（首个数字/'-'版本段之前）。
    输出到 refs/dep-src/<artifact>/，非空且未带 --force 则幂等跳过。"""
    jar = args.jar
    if not os.path.isfile(jar):
        sys.exit(f"jar 不存在：{jar}")
    name = args.artifact or os.path.basename(jar)
    # 主干名：去掉 .jar 后缀，截掉第一个版本号段（如 foo-1.2.3 → foo）
    stem = os.path.splitext(name)[0]
    for sep in ("-", "_"):
        head = stem.split(sep)[0]
        if head and any(ch.isdigit() for ch in stem[len(head):len(head) + 2][:2]):
            stem = head
            break
    out_dir = os.path.join(DST, stem)
    if os.path.isdir(out_dir) and os.listdir(out_dir) and not args.force:
        print(f"已存在 {out_dir}（非空），跳过。用 --force 重新生成。")
        return 0
    with tempfile.TemporaryDirectory() as tmp:
        outputs = decompile_with_fernflower([jar], tmp)
        if len(outputs) != 1:
            sys.exit(f"fernflower 输出异常：{outputs}")
        os.makedirs(out_dir, exist_ok=True)
        extract_jar(outputs[0], out_dir)
    print(f"[ok] 已反编译 {jar} → {out_dir}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--force", action="store_true",
                        help="输出目录已存在时也重新生成")
    sub = parser.add_subparsers(dest="mode", required=True)
    sub.add_parser("discover", help="列出编译 classpath 上的所有 jar")
    sub.add_parser("mc", help="全量反编译 Minecraft 到 refs/dep-src/minecraft/")
    p_dep = sub.add_parser("dep", help="反编译/解压单个依赖")
    p_dep.add_argument("coord", help="group:artifact:version")
    p_file = sub.add_parser("file", help="反编译任意 jar（不在 Gradle 缓存中的）")
    p_file.add_argument("jar", help="jar 文件路径")
    p_file.add_argument("artifact", nargs="?",
                        help="输出目录名（refs/dep-src/<artifact>/），缺省由文件名推断")
    args = parser.parse_args()
    try:
        return {"discover": cmd_discover, "mc": cmd_mc, "dep": cmd_dep,
                "file": cmd_file}[args.mode](args)
    except BrokenPipeError:
        return 0


if __name__ == "__main__":
    sys.exit(main())
