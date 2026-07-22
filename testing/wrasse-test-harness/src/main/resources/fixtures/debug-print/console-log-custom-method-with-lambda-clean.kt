package sample

object console {
    fun log(message: String, block: () -> Unit) {}
}

fun run() {
    console.log("debug") {
        doSomething()
    }
}

fun doSomething() {}

// expect-clean
