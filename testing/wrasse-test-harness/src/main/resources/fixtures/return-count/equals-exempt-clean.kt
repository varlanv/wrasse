package sample

class Foo(val x: Int) {
    override fun equals(other: Any?): Boolean {
        if (other !is Foo) {
            return false
        }
        if (other.x != x) {
            return false
        }
        return true
    }

    override fun hashCode(): Int = x
}

// expect-clean
