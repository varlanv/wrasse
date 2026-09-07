package sample

class Doc {
  /**
   * Some documentation
   */
  fun f() = 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
