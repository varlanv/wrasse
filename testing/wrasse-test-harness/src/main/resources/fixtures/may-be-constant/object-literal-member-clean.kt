package sample

fun example() {
    val holder = object : Runnable {
        val greeting = "hello"
        override fun run() {
            println(greeting)
        }
    }
    holder.run()
}

// expect-clean
