@file:Suppress("no-semicolons")

package sample

import kotlin.text.*

val x = 1;

// expect-error 5:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
