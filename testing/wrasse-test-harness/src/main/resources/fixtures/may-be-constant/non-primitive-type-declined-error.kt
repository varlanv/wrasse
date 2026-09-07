package sample

val greeting: Any = 1

// expect-error 3:5 may-be-constant "Property 'greeting' can be a 'const val' (no autofix for this shape)"
