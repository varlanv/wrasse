package sample

internal fun endsWithIgnoreCase(s: String, suffix: String): Boolean {
    val sl = suffix.length
    return sl <= s.length && s.regionMatches(s.length - sl, suffix, 0, sl, ignoreCase = true)
}

// expect-clean
