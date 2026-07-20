package sample

import kotlin.math.*

fun compute(): Double {
  return abs(-1.0) + PI
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 1:1 format "File is not wrasse-formatted"
