package sample

object Outer {
    object Inner {
        const val value = 1

        fun call() = 1
    }
}

val spaced = Outer.Inner /* pin */.value

val tight = Outer.Inner/* pin */.value

val called = Outer.Inner.call() /* pin */.toString()

// expect-clean
