package sample

fun demo() {
    if (true) {
        if (false) doSomething() else doOtherThing()
    }
}

fun doSomething() {}
fun doOtherThing() {}

// expect-clean
