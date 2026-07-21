package sample

interface Base {
    fun aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa()
}

class Impl : Base {
    override fun aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa() {
    }
}

// expect-error 4:9 function-name-max-length "Function name 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' is longer than the maximum length of 30"
