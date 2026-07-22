package sample

class Foo {
    /**
     * Computes something complicated.
     */
    @Suppress("no-semicolons")
    private fun compute(): Int = 1
}

// expect-error 4:5 comment-over-private-declaration "The function compute has a comment. Prefer renaming the function giving it a more self-explanatory name."
