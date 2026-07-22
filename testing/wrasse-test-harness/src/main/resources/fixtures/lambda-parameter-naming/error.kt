package sample

val f = { BadName: Int -> BadName + 1 }

// expect-error 3:11 lambda-parameter-naming "Lambda parameter name should start with a lowercase letter and use camel case, or be '_'"
