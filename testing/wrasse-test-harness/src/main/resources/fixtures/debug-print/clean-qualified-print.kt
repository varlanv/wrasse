package sample

class Printer {
    fun print(message: String) {}
}

fun run() {
    val p = Printer()
    p.print("hello")
}

// expect-clean
