package sample

data class Bar(val a: String, val b: String)

fun run() {
    val f: (Bar) -> Unit = { (HI, HELLO_THERE) -> println(HI + HELLO_THERE) }
}

// expect-error 6:31 lambda-parameter-naming "Lambda parameter name should start with a lowercase letter and use camel case, or be '_'"
// expect-error 6:35 lambda-parameter-naming "Lambda parameter name should start with a lowercase letter and use camel case, or be '_'"
