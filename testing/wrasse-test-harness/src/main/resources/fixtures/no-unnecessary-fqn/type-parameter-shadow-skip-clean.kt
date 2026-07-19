package sample

// fixture-aux-file: aux/Aux.kt

interface Container<Widget> {
    fun get(): sample.aux.Widget
}

// expect-clean
