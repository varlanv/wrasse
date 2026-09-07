package sample

import sample.aux.*

// fixture-aux-file: aux/Aliases.kt

/** Runs [block] against a fresh [Other]. */
fun run(block: (Other) -> Unit) {
    block(Other())
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
