package sample

fun <T> make(f: () -> Unit) {}

fun main() {
    make<Int> { }
}