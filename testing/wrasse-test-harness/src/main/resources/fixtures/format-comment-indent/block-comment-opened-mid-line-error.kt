package sample

fun value(): Int {
  val x = 1 /* note
      more */
  return x
}

// expect-error 1:1 format "File is not wrasse-formatted"
