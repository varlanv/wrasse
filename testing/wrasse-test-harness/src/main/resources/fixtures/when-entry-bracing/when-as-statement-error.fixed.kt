package sample

fun classify(x: Int) {
    when (x) {
        1 -> {
            println("one")
            println("more")
        }
        2 -> {
            println("two")
        }
        else -> {
            println("other")
        }
    }
}