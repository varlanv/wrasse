package sample

fun demo(names: List<String>) {
    names.forEach { name -> println(name) }
    names.forEach {}
}