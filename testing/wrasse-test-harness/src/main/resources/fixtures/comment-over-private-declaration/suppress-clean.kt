package sample

class Foo {
    /**
     * Computes something complicated.
     */
    @Suppress("comment-over-private-declaration")
    private fun compute(): Int = 1
}

// expect-clean
