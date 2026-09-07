package sample

import sample.aux.Other


/** Runs [block] against a fresh [Other]. */
fun run(block: (Other) -> Unit) {
    block(Other())
}