package sample

internal fun startsWithIgnoreCase(s: String, prefix: String): Boolean = s.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)

// expect-error 1:1 format "File is not wrasse-formatted"