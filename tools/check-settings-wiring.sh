#!/usr/bin/env bash
# 阅闻 · 设置「导出 / 恢复」接线一致性检查
#
# 为什么需要它：
#   SettingsRepository 里加一个可备份的设置项，规范动作是**四步**：
#     ① 声明 KEY_xxx
#     ② 加 xxxFlow
#     ③ 加 setXxx
#     ④ 同时进 `applySettings` 的 when 和 `BACKUP_KEYS` 白名单
#   只做前③步、漏了第④步的后果**不会立刻暴露**：
#   备份照常成功、文件里也确实带着那个值，只有等到**换机恢复**的时候
#   才会发现「这一项没回来」—— 而那时旧设备可能已经不在了。
#
#   v2.10 复查时就真的查出三个键漂了这一格：
#   `browser_pkg` / `category` / `home_sort` 从 v2.0.2、v2.2 起就在
#   BACKUP_KEYS 里，但 applySettings 一直没有对应分支，一直静默丢值。
#
# 判据（两条都必须是空集）：
#   A. 在 BACKUP_KEYS 里但 applySettings 不认识 → 导出了却恢复不回来（真 bug）
#   B. 在 applySettings 里但不在 BACKUP_KEYS   → 死分支，永远收不到值
#
# 用法：
#   bash tools/check-settings-wiring.sh          # 只报错
#   bash tools/check-settings-wiring.sh verbose  # 顺带列出全部键名
#
# 依赖：bash + sed + grep + sort + comm（macOS / Linux / Windows Git Bash 三端都有）。

set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
FILE="$HERE/../app/src/main/java/com/example/yuewen/data/SettingsRepository.kt"

if [ ! -f "$FILE" ]; then
  echo "[x] 找不到 $FILE" >&2
  exit 1
fi

VERBOSE="${1:-}"

# 注释必须先去干净：BACKUP_KEYS 列表下方就有一段「⚠️ 下面这三个键刻意不进备份」
# 的说明，里面逐条列了 KEY_BACKUP_GIST_TOKEN 之类的名字。
# 不剥注释的话它们会被当成「已导出」，这一检查就永远报不出问题。
strip_comments() { sed 's|//.*||'; }

# ⚠️ 取键名时必须**卡住左边界**（`[^A-Za-z0-9_]KEY_`），不能直接 `grep -o 'KEY_'`：
#    否则 `XKEY_HOME_SORT.name`、`MY_KEY` 这种更长标识符里的子串也会被算作命中 ——
#    而这个脚本的存在意义恰恰是「少了一个分支要报出来」，
#    漏报比误报严重得多（评审时真的踩过：注入 XKEY_ 前缀的漂移，脚本照样说 PASS）。
#    先给每行补一个前导空格，这样行首的键也能被同一个正则覆盖。
grab_keys() {
  sed 's/^/ /' | strip_comments | grep -oE '[^A-Za-z0-9_]KEY_[A-Z0-9_]+' | sed 's/^.//'
}

# ---- A. BACKUP_KEYS 白名单 ----
# 列表以 `private val BACKUP_KEYS ... = listOf(` 开头，以顶格缩进紧跟的 `)` 结束。
backup="$(sed -n '/private val BACKUP_KEYS/,/^[[:space:]]*)[[:space:]]*$/p' "$FILE" \
  | grab_keys | sort -u)"

# ---- B. applySettings 认识的键 ----
# 只在 when (name) { ... } 这一段里找 `KEY_xxx.name`；
# 不能直接全文搜 `.name`，那样会把 setXxx 里的 `prefs[KEY_X]` 之类混进来。
applied="$(sed -n '/suspend fun applySettings/,/^[[:space:]]*}[[:space:]]*$/p' "$FILE" \
  | sed 's/^/ /' | strip_comments | grep -oE '[^A-Za-z0-9_]KEY_[A-Z0-9_]+\.name' \
  | sed 's/^.//; s/\.name$//' | sort -u)"

if [ -z "$backup" ] || [ -z "$applied" ]; then
  echo "[x] 解析失败（backup=${#backup} 字符 / applied=${#applied} 字符）——" >&2
  echo "    多半是 SettingsRepository.kt 的结构变了，请同步改本脚本的 sed 范围。" >&2
  exit 1
fi

exported_only="$(comm -23 <(printf '%s\n' "$backup") <(printf '%s\n' "$applied"))"
restored_only="$(comm -13 <(printf '%s\n' "$backup") <(printf '%s\n' "$applied"))"

nb="$(printf '%s\n' "$backup" | wc -l | tr -d ' ')"
na="$(printf '%s\n' "$applied" | wc -l | tr -d ' ')"

if [ "$VERBOSE" = "verbose" ]; then
  echo "== 会写进备份文件的键（${nb}）=="
  printf '%s\n' "$backup" | sed 's/^/   /'
  echo "== 恢复时认识的键（${na}）=="
  printf '%s\n' "$applied" | sed 's/^/   /'
  echo
fi

fail=0

if [ -n "$exported_only" ]; then
  fail=1
  echo "[x] 导出了但恢复不回来（必须在 applySettings 里补分支）："
  printf '%s\n' "$exported_only" | sed 's/^/      /'
  echo "      → 不补的话，备份文件里有值，换机恢复后这一项却不会回来。"
fi

if [ -n "$restored_only" ]; then
  fail=1
  echo "[x] 能恢复但从不出现在备份里（死分支，要么补进 BACKUP_KEYS、要么删掉）："
  printf '%s\n' "$restored_only" | sed 's/^/      /'
fi

if [ "$fail" = 1 ]; then
  exit 1
fi

# ⚠️ 变量后面紧跟全角字符时必须写 `${nb}`：bash 会把 `$nb）` 里的 `）`
#    当成变量名的一部分（multibyte 字节被视作合法标识符字节），
#    于是报 `nb<乱码>: unbound variable`，看着像脚本本身坏了。
echo "[PASS] 导出白名单（${nb}）与恢复分支（${na}）一一对齐"
exit 0
