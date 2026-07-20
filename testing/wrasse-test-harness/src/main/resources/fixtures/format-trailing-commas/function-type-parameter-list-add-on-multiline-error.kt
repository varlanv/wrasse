package sample

val handler: (
    Int,
    String
) -> Unit = { _, _ -> }

// expect-error 1:1 format "File is not wrasse-formatted"
