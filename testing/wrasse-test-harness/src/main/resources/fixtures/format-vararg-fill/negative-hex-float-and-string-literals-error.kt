package sample

val mixed = listOf<Any>(-1, 0xFF, 1.0f, -2.5, 'c', "text, with comma", "(paren", 1_000_000)

// expect-error 1:1 format "File is not wrasse-formatted"
