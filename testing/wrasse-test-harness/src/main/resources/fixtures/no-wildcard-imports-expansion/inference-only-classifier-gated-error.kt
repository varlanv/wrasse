package sample

import sample.auxchain.*
// fixture-aux-file: aux/AuxChain.kt

val result = A().b.doSomething()

fun run() {
    for (item in A().items) {
        item.compute()
    }
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
