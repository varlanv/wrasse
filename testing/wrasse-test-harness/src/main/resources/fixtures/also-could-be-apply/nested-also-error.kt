package sample

class Buzz {
    fun init() {}
}

fun make(a: Buzz): Buzz = a.also { x -> x.also { it.init() } }

// expect-error 7:43 also-could-be-apply "This 'also' block contains only 'it'-qualified statements; consider 'apply' instead"
