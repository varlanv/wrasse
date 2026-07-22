package sample

interface Base {
    val property: Int
}

object Derived : Base {
    override val property = 1
}

// expect-clean
