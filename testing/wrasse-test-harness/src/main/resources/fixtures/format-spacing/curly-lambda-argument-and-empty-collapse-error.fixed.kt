package sample

fun demo(numbers: List<Int>) {
    val doubled = numbers.map({ n -> n * 2 })
    val empty = numbers.map({})
}