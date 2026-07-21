package sample

val `foo` = ""
val x = `foo`
val y = `foo`.length

// expect-error 3:5 unnecessary-backticks "Backticks are unnecessary"
// expect-error 4:9 unnecessary-backticks "Backticks are unnecessary"
// expect-error 5:9 unnecessary-backticks "Backticks are unnecessary"
