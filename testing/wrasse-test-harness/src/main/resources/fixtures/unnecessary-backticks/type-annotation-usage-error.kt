package sample

class `Foo`

val x: `Foo` = `Foo`()

// expect-error 3:7 unnecessary-backticks "Backticks are unnecessary"
// expect-error 5:8 unnecessary-backticks "Backticks are unnecessary"
// expect-error 5:16 unnecessary-backticks "Backticks are unnecessary"
