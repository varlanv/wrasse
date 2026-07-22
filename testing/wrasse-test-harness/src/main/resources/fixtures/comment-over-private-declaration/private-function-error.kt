package sample

class Foo {
    /**
     * Computes something complicated.
     */
    private fun compute(): Int = 1
}

// expect-error 4:5 comment-over-private-declaration "The function compute has a comment. Prefer renaming the function giving it a more self-explanatory name."
