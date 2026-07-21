package sample

class Box {
    val `name` = "value"
}

// expect-error 4:9 unnecessary-backticks "Backticks are unnecessary"
// expect-error 1:1 format "File is not wrasse-formatted"
