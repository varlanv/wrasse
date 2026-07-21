package sample

open class Animal

fun demo(): Animal {
    return object : Animal() {
        val name = "Rex"
    }
}