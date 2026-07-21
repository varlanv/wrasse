package sample

fun isNull(str: String) = str.equals(null)

// expect-error 3:31 equals-null-call "Calling equals() with null as the argument; use '==' to compare with null instead"
