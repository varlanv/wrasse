package sample

fun foo(f: () -> Unit) {}
fun bar(f: () -> Unit) {}

fun main() {
    foo { }
    bar { }
}