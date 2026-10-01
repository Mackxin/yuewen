package com.example.yuewen.data.util

/**
 * 极简 JSON 工具（v2.0）。
 *
 * 为什么不用 `org.json`：本机没有真机/模拟器，唯一的自动化验证手段是
 * `tools/jvmtest` 那个**纯 JVM** 编译器 —— 它跑在桌面 JDK 上，
 * 那里没有 Android 的 `org.json`。备份 / 恢复、订阅源搜索的解析都在这条链路上，
 * 所以自己写一份小而完整的实现：生成 + 解析都在 Kotlin 里，能离线测。
 *
 * 功能范围（够用就好，不做完整实现）：
 * - 生成：[quote] / [obj] / [array]
 * - 解析：[parse] 返回 [J] 树，支持 object / array / string / number / true / false / null
 * - 辅助：[J.asObject] / [J.asArray] / [J.asString] / [J.asStringOr] / [J.asLongOr]
 *
 * 不支持的：注释、单引号字符串、尾随逗号 —— 我们的输入都是自己生成或标准 API 返回的。
 */
// ---------------------------------------------------------------------- 取值辅助
//
// 刻意写成**文件顶层的扩展函数**而不是 object 里的成员扩展：
// 成员扩展只有在 `with(Json) { … }` 的作用域里才看得见，调用方到处都得包一层；
// 顶层扩展 import 一下就能直接用（`import com.example.yuewen.data.util.asString`）。

fun Json.J?.asObject(): Map<String, Json.J> = (this as? Json.J.O)?.v ?: emptyMap()

fun Json.J?.asArray(): List<Json.J> = (this as? Json.J.A)?.v ?: emptyList()

/** 取字符串值；数字 / 布尔也顺手转成文本，方便统一处理。 */
fun Json.J?.asString(): String = when (this) {
    is Json.J.S -> v
    is Json.J.N -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    is Json.J.B -> v.toString()
    Json.J.Null -> ""
    else -> ""
}

fun Json.J?.asLongOr(def: Long = 0L): Long = when (this) {
    is Json.J.N -> v.toLong()
    is Json.J.S -> v.toLongOrNull() ?: def
    is Json.J.B -> if (v) 1L else 0L
    else -> def
}

fun Json.J?.asBooleanOr(def: Boolean = false): Boolean = when (this) {
    is Json.J.B -> v
    is Json.J.S -> v.equals("true", true) || v == "1"
    is Json.J.N -> v != 0.0
    else -> def
}

object Json {

    // ------------------------------------------------------------------ 生成

    /** 转义成 JSON 字符串内容（不含两端的引号）。 */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 带引号的 JSON 字符串。 */
    fun quote(s: String): String = "\"" + esc(s) + "\""

    /**
     * 生成对象。值支持：String / 其他基础类型（直接 toString）/ Boolean / null / 原始 JSON 片段。
     * 用 [Raw] 包一层表示「这已经是一段合法 JSON，别再转义」（嵌套对象/数组用）。
     */
    fun obj(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(",", "{", "}") { (k, v) -> quote(k) + ":" + value(v) }

    /** 生成数组。 */
    fun array(items: List<Any?>): String = items.joinToString(",", "[", "]") { value(it) }

    /** 标记「原样输出」的 JSON 片段。 */
    class Raw(val text: String)

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.text
        is String -> quote(v)
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> quote(v.toString())
    }

    // ------------------------------------------------------------------ 解析

    sealed interface J {
        data class S(val v: String) : J
        data class N(val v: Double) : J
        data class B(val v: Boolean) : J
        data object Null : J
        data class A(val v: List<J>) : J
        data class O(val v: Map<String, J>) : J
    }

    /** 解析失败返回 null（调用方自己兜底，不要抛异常把界面打崩）。 */
    fun parse(text: String): J? = runCatching { Parser(text).run() }.getOrNull()

    /** 顶层是对象数组时的便捷入口：直接拿到 `List<Map<String, J>>`。 */
    fun parseArray(text: String): List<Map<String, J>> =
        parse(text)?.asArray()?.map { it.asObject() } ?: emptyList()

    private class Parser(private val s: String) {
        private var i = 0

        fun run(): J {
            skipWs()
            val v = readValue()
            skipWs()
            return v
        }

        private fun readValue(): J {
            skipWs()
            if (i >= s.length) error("unexpected end")
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> J.S(readString())
                't' -> { expect("true"); J.B(true) }
                'f' -> { expect("false"); J.B(false) }
                'n' -> { expect("null"); J.Null }
                else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected '$c' at $i")
            }
        }

        private fun readObject(): J {
            i++ // {
            val map = LinkedHashMap<String, J>()
            skipWs()
            if (peek() == '}') { i++; return J.O(map) }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                require(peek() == ':') { "expect ':' at $i" }
                i++
                map[key] = readValue()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return J.O(map) }
                    else -> error("expect ',' or '}' at $i")
                }
            }
        }

        private fun readArray(): J {
            i++ // [
            val list = ArrayList<J>()
            skipWs()
            if (peek() == ']') { i++; return J.A(list) }
            while (true) {
                list.add(readValue())
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return J.A(list) }
                    else -> error("expect ',' or ']' at $i")
                }
            }
        }

        private fun readString(): String {
            require(peek() == '"') { "expect '\"' at $i" }
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) break
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = s.substring(i, minOf(i + 4, s.length))
                                i += hex.length
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            error("unterminated string")
        }

        private fun readNumber(): J {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return J.N(s.substring(start, i).toDouble())
        }

        private fun expect(word: String) {
            require(s.startsWith(word, i)) { "expect '$word' at $i" }
            i += word.length
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
