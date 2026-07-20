package sample

import java.util.*

val x = 1;

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
// expect-error 5:10 no-semicolons "Unnecessary semicolon"
