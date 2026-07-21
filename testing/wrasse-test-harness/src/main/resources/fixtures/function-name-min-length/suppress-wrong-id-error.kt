package sample

@Suppress("no-semicolons")
fun ab() {
}

// expect-error 4:5 function-name-min-length "Function name 'ab' is shorter than the minimum length of 3"
