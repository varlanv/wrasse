package sample

@Suppress("lambda-parameter-naming")
val f = { BadName: Int -> BadName + 1 }

// expect-clean
