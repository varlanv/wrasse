package sample

fun foo() = 1

fun use() {
    foo()
    val ref = ::foo
}