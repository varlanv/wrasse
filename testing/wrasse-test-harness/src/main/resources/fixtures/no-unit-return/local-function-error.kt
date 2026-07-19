package sample

fun outer() {
    fun inner(): Unit {}
}

// expect-error 4:16 no-unit-return "Redundant Unit return type"
