package sample

class Sample {
    private lateinit var e: Exception

    fun f() {
        try {
            println("work")
        } catch (e: IllegalStateException) {
            throw this.e
        }
    }
}

// expect-clean
