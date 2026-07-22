package sample

fun x(a: String, b: String): Boolean = a.equals(b.equals(null))

// expect-error 3:51 equals-null-call "Calling equals() with null as the argument; use '==' to compare with null instead"
