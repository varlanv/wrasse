package sample

fun run() {
    print("debug")
}

// expect-error 4:5 debug-print "'print()' looks like leftover debug output; remove it or replace it with a logger."
