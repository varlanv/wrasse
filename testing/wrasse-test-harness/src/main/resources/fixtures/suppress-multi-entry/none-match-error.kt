@file:Suppress("unused")

package sample

import kotlin.text.*

val x = 1;

// expect-error 5:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 7:10 no-semicolons "Unnecessary semicolon"
