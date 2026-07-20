package sample

import kotlin.math.*
import kotlin.text.Regex

class Box {
  val x = 1;
    fun show(): Double {
        if (x > 0)
            return abs(-1.0) + PI
        else
            return 0.0
    }
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 7:12 no-semicolons "Unnecessary semicolon"
// expect-error 10:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 12:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 1:1 format "File is not wrasse-formatted"
