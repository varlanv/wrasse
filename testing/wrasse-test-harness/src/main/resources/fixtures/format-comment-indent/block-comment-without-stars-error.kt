package sample

class Doc {
  /*
    plain line
      deeper line
  */
  fun f() = 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
