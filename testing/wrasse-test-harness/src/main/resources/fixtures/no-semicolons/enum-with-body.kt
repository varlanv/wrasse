package sample

enum class Color {
    RED,
    GREEN,
    BLUE;

    fun display() = name.lowercase()
}

enum class Empty {
    ;
    fun foo() = "bar"
}

// expect-clean
