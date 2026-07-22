package sample

@Suppress("no-semicolons")
fun run() {
    print("debug")
}

// expect-error 5:5 debug-print "'print()' looks like leftover debug output; remove it or replace it with a logger."
