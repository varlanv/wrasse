package sample

fun demo() {
    if (true)
        doSomething() // trailing
    else {
        doOtherThing()
    }
}

fun doSomething() {}
fun doOtherThing() {}