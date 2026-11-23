package sample

enum class Color { RED }

class Marker

val a = 1
val values = listOf<Any?>(-a, +a, !true, a as Any, null, true, "$a", "${a + 1}", ::println, Color.RED, Marker())

// expect-error 1:1 format "File is not wrasse-formatted"
