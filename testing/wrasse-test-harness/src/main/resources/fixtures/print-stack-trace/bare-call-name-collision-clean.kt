package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        fun printStackTrace() {}
        printStackTrace()
    }
}

// expect-clean
