package sample

@Deprecated("weird annotation with\t\t\t")
val s = "This rule is awesome"

// expect-error 3:13 string-should-be-raw-string "String with escape characters should be converted to a raw string"
