#!/usr/bin/env bash
# =============================================================================
# 构建 kuromoji-ipadic 日语分词引擎的动态加载 jar
#
# 用途：kuromoji 已从 APK 依赖中移除（省 ~12.7MB），改为首次使用日语注音时
#       由 KuromojiEngine 按需下载本产物。产物升级后需：
#         1. 重跑本脚本，把新 sha256 / size 回填到
#            app/.../utils/KuromojiEngine.kt 的 ENGINE_SHA256 / ENGINE_SIZE_BYTES
#         2. 以新 Release tag（或覆盖旧 tag 的 assets）发布
# 依赖：ANDROID_SDK/build-tools/<v>/d8.bat、JDK、python3
# 用法：SDK_BUILD_TOOLS="C:\path\to\build-tools\37.0.0" SDK_PLATFORM="C:\path\to\platforms\android-37" ./build_engine_jar.sh
# =============================================================================
set -euo pipefail

SDK_BUILD_TOOLS="${SDK_BUILD_TOOLS:?set SDK_BUILD_TOOLS}"
SDK_PLATFORM="${SDK_PLATFORM:?set SDK_PLATFORM}"
VERSION_CORE="${VERSION_CORE:-0.9.0}"
VERSION_IPADIC="${VERSION_IPADIC:-0.9.0}"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

CORE_JAR=$(find "$GRADLE_CACHE/com.atilika.kuromoji/kuromoji-core/$VERSION_CORE" -name "*.jar" | head -1)
IPADIC_JAR=$(find "$GRADLE_CACHE/com.atilika.kuromoji/kuromoji-ipadic/$VERSION_IPADIC" -name "*.jar" | head -1)
[ -f "$CORE_JAR" ] && [ -f "$IPADIC_JAR" ] || { echo "kuromoji jars not found; run a gradle sync once"; exit 1; }

# 1) 两个 jar 的字节码合并为单个 classes.dex（d8）
"$SDK_BUILD_TOOLS/d8.bat" --release --min-api 23 \
    --lib "$SDK_PLATFORM/android.jar" \
    --output "$WORK" \
    "$CORE_JAR" "$IPADIC_JAR"

# 2) classes.dex + 词典资源（非 class、非 META-INF）打包成自包含 jar
python - "$WORK" "$CORE_JAR" "$IPADIC_JAR" <<'PY'
import sys, zipfile, hashlib

work, core, ipadic = sys.argv[1], sys.argv[2], sys.argv[3]
out = "kuromoji-ipadic-android.jar"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.write(f"{work}/classes.dex", "classes.dex")
    for src in (core, ipadic):
        with zipfile.ZipFile(src) as s:
            for n in s.namelist():
                if n.endswith(".class") or n.startswith("META-INF") or n.endswith("/"):
                    continue
                z.writestr(n, s.read(n))
data = open(out, "rb").read()
print(f"built {out}: {len(data)} bytes")
print(f"sha256: {hashlib.sha256(data).hexdigest()}")
PY
