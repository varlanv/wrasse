package sample

import sample.auxdelegate.Box
import sample.auxdelegate.provideDelegate

// fixture-aux-file: aux/Delegate.kt

class Holder {
    private val thing by Box()
}

// expect-clean
