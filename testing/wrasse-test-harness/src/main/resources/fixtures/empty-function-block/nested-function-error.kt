package sample

fun a() {
    fun b() {
    }
    b()
}

// expect-error 4:13 empty-function-block "Empty function block detected. Empty blocks of code serve no purpose and should be removed"
