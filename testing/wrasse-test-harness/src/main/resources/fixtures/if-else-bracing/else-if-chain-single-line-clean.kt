package sample

fun demo() {
    if (true) doSomething() else if (false) doOtherThing() else doThirdThing()
}

fun doSomething() {}
fun doOtherThing() {}
fun doThirdThing() {}

// expect-clean
