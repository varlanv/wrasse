package sample

class Factory {
    fun make(vararg xs: Int) = xs
}

val f = Factory()

// wrasse columns:2
val t = f.make(
    1, 2,
    3, 4,
)