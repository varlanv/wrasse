package sample

import sample.auxkdoc.usedFn
import sample.auxkdoc.linkedTarget
import sample.auxkdoc.displayOnly

// fixture-aux-file: aux/KdocLink.kt

/**
 * See [displayOnly][linkedTarget] for details.
 */
fun doc() {
    usedFn()
}

// expect-clean
