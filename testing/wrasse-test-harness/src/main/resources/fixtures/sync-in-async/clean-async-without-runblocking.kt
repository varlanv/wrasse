package sample

fun async(block: () -> Unit) {}

fun start() {
    async {
        println("no blocking here")
    }
}

// expect-clean
