package sample

fun demo() {
    if (outer)
        if (inner) {
            doSomething()
        } else {
            doOtherThing()
        }
}

val outer = true
val inner = false
fun doSomething() {}
fun doOtherThing() {}