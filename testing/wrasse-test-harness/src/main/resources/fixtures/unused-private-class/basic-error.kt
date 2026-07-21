package sample

private class Unused

fun use() {
    println("hi")
}

// expect-error 3:1 unused-private-class "Private class 'Unused' is unused"
