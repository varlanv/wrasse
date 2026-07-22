package sample

object console {
    fun log(message: String) {}
}

fun run() {
    console.log("debug")
}

// expect-error 8:5 debug-print "'console.log()' looks like leftover debug output; remove it or replace it with a logger."
