package sample

class Entry(val kind: String, val text: String)

fun classify(
    kind: String,
    text: String,
    start: Int,
    end: Int,
): Entry = when (kind) {
    "comment" -> Entry(
        kind,
        normalizeCommentTextWithAVeryLongHelperName(text, start, end, preserveTrailingWhitespace = false),
    )
    "code" -> Entry(kind, text)
    else -> Entry(kind, text.trim())
}

fun normalizeCommentTextWithAVeryLongHelperName(
    text: String,
    start: Int,
    end: Int,
    preserveTrailingWhitespace: Boolean,
): String = text.substring(start, end)