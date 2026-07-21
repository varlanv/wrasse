package sample

fun demo() {
    val process = { value: Int ->
        fun square(x: Int): Int {
            return x * x
        }
        square(value)
    }
    println(process(4))
}

// expect-clean
