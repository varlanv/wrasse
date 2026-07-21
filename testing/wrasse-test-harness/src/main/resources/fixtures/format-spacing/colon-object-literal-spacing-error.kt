package sample

open class Animal

fun demo(): Animal {
    return object :Animal() {
        val name = "Rex"
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
