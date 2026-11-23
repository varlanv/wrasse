package sample

fun run(block: () -> Unit) = block()

fun main() {
    run {
        val table = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90)
        println(table.size)
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
