package sample

class Greeter {
    fun greet(names: List<String>) {
        if (names.isEmpty()) {
            println("nobody")
        } else {
            names.forEach { name ->
                println("hello, " + name)
            }
        }
    }
}

// expect-clean
