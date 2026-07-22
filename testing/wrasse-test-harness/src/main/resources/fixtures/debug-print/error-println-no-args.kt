package sample

fun run() {
    println()
}

// expect-error 4:5 debug-print "'println()' looks like leftover debug output; remove it or replace it with a logger."
