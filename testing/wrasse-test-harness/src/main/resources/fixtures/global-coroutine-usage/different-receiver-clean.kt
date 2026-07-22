package sample

class Scope {
    fun launch(block: () -> Unit) {}
}

fun start() {
    val myScope = Scope()
    myScope.launch { }
}

// expect-clean
