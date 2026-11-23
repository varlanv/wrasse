package sample

val text = listOf(1, 2)
    .map { it  +  1 }
    // wrasse fmt:off
    .filter {   it   >   1   }
    // wrasse fmt:on
    .size

// expect-error 1:1 format "File is not wrasse-formatted"
