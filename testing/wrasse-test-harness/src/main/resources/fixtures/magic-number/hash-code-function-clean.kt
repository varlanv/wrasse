package sample

class Point(val x: Int, val y: Int) {
    override fun hashCode(): Int {
        print(42)
        return 31
    }
}

// expect-clean
