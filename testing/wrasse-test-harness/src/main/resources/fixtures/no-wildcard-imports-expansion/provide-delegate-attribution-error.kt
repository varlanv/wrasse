package sample

import sample.auxdelegate.*
// fixture-aux-file: aux/AuxDelegate.kt

class Holder {
    private val thing by Box()
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
