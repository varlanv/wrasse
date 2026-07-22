package sample

class Outer {
    private class Unused

    fun use() {
        println("hi")
    }
}

// expect-error 4:5 unused-private-class "Private class 'Unused' is unused"
