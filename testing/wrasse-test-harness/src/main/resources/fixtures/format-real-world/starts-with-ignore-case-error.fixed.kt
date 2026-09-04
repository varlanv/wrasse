package sample

internal fun startsWithIgnoreCase(
    s: String,
    prefix: String,
): Boolean = s.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)