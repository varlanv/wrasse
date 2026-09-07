package sample

class Doc {
  /**
   * First

   * Second
   */
  fun f() = 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
