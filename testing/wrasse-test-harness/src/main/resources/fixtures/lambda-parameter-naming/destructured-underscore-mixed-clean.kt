package sample

data class Bar(val a: String, val b: String)

fun run() {
    val f: (Bar) -> Unit = { (_, b) -> println(b) }
}

// expect-clean
