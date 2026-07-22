package sample

class Foo {
    /**
     * The current count.
     */
    private val count: Int = 1
}

// expect-error 4:5 comment-over-private-declaration "Private properties should be named in a self-explanatory manner without the need for a comment."
