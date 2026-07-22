package sample

@Suppress("no-semicolons")
class Container {
    /*
     * Block 1
     */
    // eol comment
    fun foo() {}
}

// expect-error 8:5 no-consecutive-comments "an EOL comment may not be preceded by a block comment unless separated by a blank line"
