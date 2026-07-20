package sample

fun demo() {
    if (if (true) true else false) {
        if (true) true else false
    } else {
        println(if (true) true else false)
    }
}