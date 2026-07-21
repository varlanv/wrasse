package sample

fun `foo`() = 1

fun use() {
    `foo`()
    val ref = ::`foo`
}

// expect-error 3:5 unnecessary-backticks "Backticks are unnecessary"
// expect-error 6:5 unnecessary-backticks "Backticks are unnecessary"
// expect-error 7:17 unnecessary-backticks "Backticks are unnecessary"
