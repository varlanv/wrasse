package sample

class Container(
    /** Doc for bar */
    val bar: Int,
) {
    /** Doc for ctor */
    constructor() : this(0)
}

enum class Color {
    /** Doc for RED */
    RED,
}

// expect-clean
