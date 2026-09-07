package sample

object Outer {
    object Inner {
        const val value = 1
    }
}

val joined = Outer.Inner /* pin */.value