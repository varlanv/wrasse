package sample

/**
 * Comment
 * [prop1] - non-public property
 */
@Suppress("no-semicolons")
class Test {
    private val prop1 = 0
}

// expect-error 9:17 kdoc-references-non-public-property "The property 'prop1' is non-public and should not be referenced from KDoc comments."
