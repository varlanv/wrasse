package sample

fun demo(strings: List<String>): List<Int> {
    return strings.map(String::length)
}