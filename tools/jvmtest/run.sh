#!/usr/bin/env bash
# 阅闻 · 解析层离线测试（不需要安卓设备 / 模拟器）
#
# 为什么需要它：本机没有 AVD 也没有真机，Compose 界面没法自动化跑；
# 但「RSS 解析 / OPML 导入导出 / 格式识别」这几块是纯 JVM 逻辑，
# 抽出来用独立的 Kotlin 编译器跑一遍，几秒钟就能发现回归。
#
# 用法：
#   bash tools/jvmtest/run.sh            # 只看 PASS/FAIL 摘要
#   bash tools/jvmtest/run.sh verbose    # 打印每条的详细信息
#
# 依赖：本机跑过一次 gradle 构建（这样 ~/.gradle 缓存里才有下面这些 jar）。

set -uo pipefail

JDK="${JAVA_HOME:-C:/android-env/jdk}"
JAVA="$JDK/bin/java.exe"

# ⚠️ 必须把路径转成 Windows 形式（C:/...）：
#    Git Bash 的 pwd 给的是 /c/Users/...，而 Windows 版 java.exe 不认这种写法，
#    拼进 -cp 之后会直接报「找不到主类」。cygpath -m 负责转换。
winpath() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

HERE="$(winpath "$(cd "$(dirname "$0")" && pwd)")"
ROOT="$(winpath "$(cd "$HERE/../.." && pwd)")"
SRC="$ROOT/app/src/main/java/com/example/yuewen"
OUT="$HERE/.out"

GRADLE_CACHE="${GRADLE_CACHE:-$(winpath "$HOME")/.gradle/caches/modules-2/files-2.1}"
[ -d "$GRADLE_CACHE" ] || GRADLE_CACHE="C:/Users/Administrator/.gradle/caches/modules-2/files-2.1"

# 按通配符在缓存里定位 jar（用 bash 自带的 glob，不扫盘）。
# gradle 缓存结构固定为 group/artifact/version/hash/file.jar
resolve() {
  local f
  for f in "$@"; do
    [ -f "$f" ] && { printf '%s' "$f"; return 0; }
  done
  return 1
}

need() {
  local hit
  hit="$(resolve "$@")"
  if [ -z "$hit" ]; then
    echo "[x] 找不到依赖：$1" >&2
    echo "    先跑一次 gradle 构建让缓存就绪，或手动设置 GRADLE_CACHE。" >&2
    return 1
  fi
  printf '%s' "$hit"
}

K="$GRADLE_CACHE/org.jetbrains.kotlin"
# 版本号要锁死：缓存里常常并存多个 kotlin-reflect（1.6.x / 1.8.x / 1.9.x），
# 拿错版本会让编译器起不来，所以 glob 里带上 1.9。
KC=$(need "$K"/kotlin-compiler-embeddable/1.9.24/*/kotlin-compiler-embeddable-1.9.24.jar) || exit 1
STDLIB=$(need "$K"/kotlin-stdlib/1.9.24/*/kotlin-stdlib-1.9.24.jar) || exit 1
REFLECT=$(need "$K"/kotlin-reflect/1.9.2*/*/kotlin-reflect-1.9.2*.jar) || exit 1
SCRIPT=$(need "$K"/kotlin-script-runtime/1.9.24/*/kotlin-script-runtime-1.9.24.jar) || exit 1
DAEMON=$(need "$K"/kotlin-daemon-embeddable/1.9.24/*/kotlin-daemon-embeddable-1.9.24.jar) || exit 1
TROVE=$(need "$GRADLE_CACHE"/org.jetbrains.intellij.deps/trove4j/*/*/trove4j-*.jar) || exit 1
ANN=$(need "$GRADLE_CACHE"/org.jetbrains/annotations/13.0/*/annotations-13.0.jar) || exit 1
KXML2=$(need "$GRADLE_CACHE"/net.sf.kxml/kxml2/*/*/kxml2-*.jar) || exit 1
# v1.7：BodyBlocks（正文块解析）依赖 jsoup
JSOUP=$(need "$GRADLE_CACHE"/org.jsoup/jsoup/1.16.2/*/jsoup-1.16.2.jar) || exit 1
# v2.0：Note / Article 带 Room 注解（@Entity / @PrimaryKey）。
# 它们本身是纯数据类，只是头上贴了注解 —— 把 room-common 挂上就能在桌面 JDK 里编过，
# 于是「备份 / 恢复」这条链路也能进离线回归（room-common 只是个注解 jar，没有 Android 依赖）。
ROOM=$(need "$GRADLE_CACHE"/androidx.room/room-common/2.6.1/*/room-common-2.6.1.jar) || exit 1

COMPILER_CP="$KC;$STDLIB;$REFLECT;$SCRIPT;$DAEMON;$TROVE;$ANN"

# 被测源码（纯 JVM，不碰 android.*）
# ⚠️ 加新文件时要留意它的依赖：ReadStats.kt 引用了 SourceCount，
#    所以 SourceCount 必须待在 data/model 包（不能放在 ArticleDao.kt 里，
#    那个文件带 Room 注解，纯 JVM 编不过）。
SOURCES=(
  "$SRC/data/model/FeedSource.kt"
  "$SRC/data/model/ReadStats.kt"
  "$SRC/data/model/BodyBlock.kt"
  "$SRC/data/model/Article.kt"
  "$SRC/data/model/Note.kt"
  "$SRC/data/model/FeedCatalog.kt"
  "$SRC/data/util/Json.kt"
  "$SRC/data/reader/BodyBlocks.kt"
  "$SRC/data/rss/RssParser.kt"
  "$SRC/data/rss/FeedSearchParser.kt"
  "$SRC/data/rss/RssHub.kt"
  "$SRC/data/opml/Opml.kt"
  "$SRC/data/backup/Backup.kt"
  "$SRC/ui/util/TtsChunker.kt"
  # v2.2：首页排序是纯函数（不碰 android.*），挂进来才能测。
  # ⚠️ 它引用 Article，而 Article 带 Room 注解 —— 所以上面那个 ROOM jar 必须在，
  #    别看着「就一个排序」就以为不需要 room-common。
  "$SRC/ui/util/HomeSort.kt"
  # v2.3：配色生成器（纯 ARGB Long 运算，不碰 Compose）与 SQL LIKE 转义。
  # 这两个都是「光看代码看不出对错」的那类逻辑 —— 对比度够不够、通配符转没转义，
  # 全靠这里的断言兜住，所以一定要挂进来。
  "$SRC/ui/theme/PaletteGen.kt"
  "$SRC/data/util/SqlLike.kt"
  # v2.4：首页关键词胶囊的清洗与匹配（纯字符串逻辑）。
  # 这两个函数直接决定「首页点一下胶囊还剩几篇文章」—— 去重规则写错会冒出重复胶囊，
  # 匹配漏了正文会出现「明明有文章却筛出空的」，都只能靠断言兜住。
  "$SRC/data/util/HomeKeyword.kt"
  "$HERE/TestMain.kt"
)

echo "== 编译 =="
rm -rf "$OUT" && mkdir -p "$OUT"
"$JAVA" -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -nowarn -cp "$STDLIB;$KXML2;$JSOUP;$ROOM" -d "$OUT" "${SOURCES[@]}" || exit 1

echo "== 运行 =="
if [ "${1:-}" = "verbose" ]; then
  "$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT;$STDLIB;$KXML2;$JSOUP;$ROOM" TestMainKt
else
  "$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT;$STDLIB;$KXML2;$JSOUP;$ROOM" TestMainKt \
    | grep -E '^\[(PASS|FAIL)\]|^通过|^===='
fi
