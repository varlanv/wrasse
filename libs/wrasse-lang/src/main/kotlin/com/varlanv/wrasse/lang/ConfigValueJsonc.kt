package com.varlanv.wrasse.lang

class ConfigValueJsonc private constructor(private val input: String) {
    private var pos = 0
    private var depth = 0

    companion object {
        const val MAX_DEPTH = 20

        fun parse(input: String): ConfigValue {
            val parser = ConfigValueJsonc(input)
            parser.skipWsAndComments()
            if (parser.pos >= input.length) parser.error("Empty input")
            val value = parser.readValue()
            parser.skipWsAndComments()
            if (parser.pos < input.length) parser.error("Unexpected trailing content")
            return value
        }
    }

    private fun readValue(): ConfigValue {
        skipWsAndComments()
        if (pos >= input.length) error("Unexpected end of input")
        return when (input[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> ConfigValue.Str(readString())
            't', 'f' -> readBool()
            'n' -> readNull()
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected character '${input[pos]}'")
        }
    }

    private fun readObject(): ConfigValue.Obj {
        if (depth >= MAX_DEPTH) error("Nesting exceeds $MAX_DEPTH levels")
        depth++
        pos++
        skipWsAndComments()
        val map = LinkedHashMap<String, ConfigValue>()
        if (pos < input.length && input[pos] == '}') {
            pos++
            depth--
            return ConfigValue.Obj(SafeProperties(map))
        }
        while (true) {
            skipWsAndComments()
            if (pos >= input.length) error("Unterminated object")
            if (input[pos] != '"') error("Expected string key, got '${input[pos]}'")
            val key = readString()
            skipWsAndComments()
            if (pos >= input.length || input[pos] != ':') error("Expected ':'")
            pos++
            val value = readValue()
            map[key] = value
            skipWsAndComments()
            if (pos >= input.length) error("Unterminated object")
            when (input[pos]) {
                ',' -> {
                    pos++
                    skipWsAndComments()
                    if (pos < input.length && input[pos] == '}') {
                        pos++
                        break
                    }
                }
                '}' -> {
                    pos++
                    break
                }
                else -> error("Expected ',' or '}'")
            }
        }
        depth--
        return ConfigValue.Obj(SafeProperties(map))
    }

    private fun readArray(): ConfigValue {
        if (depth >= MAX_DEPTH) error("Nesting exceeds $MAX_DEPTH levels")
        depth++
        pos++
        skipWsAndComments()
        val elements = mutableListOf<ConfigValue>()
        if (pos < input.length && input[pos] == ']') {
            pos++
            depth--
            return ConfigValue.StrArr(emptyList())
        }
        while (true) {
            elements.add(readValue())
            skipWsAndComments()
            if (pos >= input.length) error("Unterminated array")
            when (input[pos]) {
                ',' -> {
                    pos++
                    skipWsAndComments()
                    if (pos < input.length && input[pos] == ']') {
                        pos++
                        break
                    }
                }
                ']' -> {
                    pos++
                    break
                }
                else -> error("Expected ',' or ']'")
            }
        }
        depth--
        return toTypedArray(elements)
    }

    private fun toTypedArray(elements: List<ConfigValue>): ConfigValue {
        if (elements.isEmpty()) return ConfigValue.StrArr(emptyList())
        return when {
            elements.all { it is ConfigValue.Str } ->
                ConfigValue.StrArr(elements.map { (it as ConfigValue.Str).value })
            elements.all { it is ConfigValue.Num } ->
                ConfigValue.NumArr(elements.map { (it as ConfigValue.Num).value })
            elements.all { it is ConfigValue.Obj } ->
                ConfigValue.ObjArr(elements.map { (it as ConfigValue.Obj).value })
            else ->
                ConfigValue.Arr(elements)
        }
    }

    private fun readString(): String {
        pos++
        val sb = StringBuilder()
        while (pos < input.length) {
            when (val c = input[pos]) {
                '"' -> {
                    pos++
                    return sb.toString()
                }
                '\\' -> {
                    pos++
                    if (pos >= input.length) error("Unterminated string escape")
                    when (val esc = input[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 >= input.length) error("Incomplete unicode escape")
                            val hex = input.substring(pos + 1, pos + 5)
                            val code = hex.toIntOrNull(16)
                                ?: error("Invalid unicode escape: \\u$hex")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> error("Invalid escape: \\$esc")
                    }
                    pos++
                }
                else -> {
                    if (c < ' ') error("Unescaped control character in string")
                    sb.append(c)
                    pos++
                }
            }
        }
        error("Unterminated string")
    }

    private fun readNumber(): ConfigValue {
        val start = pos
        if (input[pos] == '-') pos++
        if (pos >= input.length) error("Unexpected end in number")
        if (input[pos] == '0') {
            pos++
            if (pos < input.length && input[pos] in '0'..'9') error("Leading zeros not allowed")
        } else if (input[pos] in '1'..'9') {
            while (pos < input.length && input[pos] in '0'..'9') pos++
        } else {
            error("Expected digit")
        }
        var isDouble = false
        if (pos < input.length && input[pos] == '.') {
            isDouble = true
            pos++
            if (pos >= input.length || input[pos] !in '0'..'9') error("Expected digit after '.'")
            while (pos < input.length && input[pos] in '0'..'9') pos++
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            isDouble = true
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            if (pos >= input.length || input[pos] !in '0'..'9') error("Expected digit in exponent")
            while (pos < input.length && input[pos] in '0'..'9') pos++
        }
        val numStr = input.substring(start, pos)
        return if (isDouble) {
            ConfigValue.Dbl(numStr.toDouble())
        } else {
            ConfigValue.Num(numStr.toLong())
        }
    }

    private fun readBool(): ConfigValue.Bool {
        if (input.startsWith("true", pos)) {
            pos += 4
            return ConfigValue.Bool(true)
        }
        if (input.startsWith("false", pos)) {
            pos += 5
            return ConfigValue.Bool(false)
        }
        error("Expected 'true' or 'false'")
    }

    private fun readNull(): ConfigValue.Null {
        if (!input.startsWith("null", pos)) error("Expected 'null'")
        pos += 4
        return ConfigValue.Null
    }

    private fun skipWsAndComments() {
        while (pos < input.length) {
            val c = input[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++
                continue
            }
            if (c == '/' && pos + 1 < input.length) {
                if (input[pos + 1] == '/') {
                    pos += 2
                    while (pos < input.length && input[pos] != '\n') pos++
                    if (pos < input.length) pos++
                    continue
                }
                if (input[pos + 1] == '*') {
                    val start = pos
                    pos += 2
                    var found = false
                    while (pos + 1 < input.length) {
                        if (input[pos] == '*' && input[pos + 1] == '/') {
                            pos += 2
                            found = true
                            break
                        }
                        pos++
                    }
                    if (!found) error("Unterminated block comment at position $start")
                    continue
                }
            }
            break
        }
    }

    private fun error(msg: String): Nothing {
        throw IllegalArgumentException("$msg (at position $pos)")
    }
}
