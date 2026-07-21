package sample

@Suppress("no-semicolons")
val s = "line1\nline2\nline3\n"

// expect-error 4:9 string-should-be-raw-string "String with escape characters should be converted to a raw string"
