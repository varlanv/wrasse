package sample

class Runner {
    fun main() {
        throw IllegalStateException("boom")
    }
}

// expect-clean
