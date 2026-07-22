package sample

object console {
    fun debug(message: String) {}
}

fun run() {
    console.debug("debug")
}

// expect-clean
