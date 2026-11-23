package sample

fun main() {
    // wrasse fmt:off
    val   a   =   1
    class bad_name
    // wrasse fmt:on
    val   b   =   2
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:11 class-naming "Class or object name should start with an uppercase letter and use camel case"
