package sample

@Suppress("no-semicolons")
fun isNull(str: String) = str.equals(null)

// expect-error 4:31 equals-null-call "Calling equals() with null as the argument; use '==' to compare with null instead"
