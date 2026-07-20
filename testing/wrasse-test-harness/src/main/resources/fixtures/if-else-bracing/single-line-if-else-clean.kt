package sample

fun demo() {
    if (true) doSomething() else doOtherThing()
}

fun doSomething() {}
fun doOtherThing() {}

// expect-clean
