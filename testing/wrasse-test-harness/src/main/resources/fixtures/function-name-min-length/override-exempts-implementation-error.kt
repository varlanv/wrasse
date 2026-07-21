package sample

interface Base {
    fun ab()
}

class Impl : Base {
    override fun ab() {
    }
}

// expect-error 4:9 function-name-min-length "Function name 'ab' is shorter than the minimum length of 3"
