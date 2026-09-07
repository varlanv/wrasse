package sample

class Doc {
  /**
   * Example:
     ```
     val x = 1
     ```
   */
  fun f() = 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
