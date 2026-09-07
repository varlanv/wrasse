package sample

fun value(): Int {
  /* note
      more */ val x = 1
  return x
}

// expect-error 1:1 format "File is not wrasse-formatted"
