package sample

import kotlin.math.abs
import kotlin.text.Regex

class Box {
  fun show(): Int {
    return abs(-1)
  }
}

// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 1:1 format "File is not wrasse-formatted"
