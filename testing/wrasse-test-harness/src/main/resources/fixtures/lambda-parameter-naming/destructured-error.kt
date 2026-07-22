package sample

fun run() {
    mapOf(1 to 2).forEach { (BadKey, value) -> println(BadKey to value) }
}

// expect-error 4:30 lambda-parameter-naming "Lambda parameter name should start with a lowercase letter and use camel case, or be '_'"
