package sample

/**
 * Comment
 * [prop1] - internal property
 */
class Test {
    internal val prop1 = 0
}

// expect-error 8:18 kdoc-references-non-public-property "The property 'prop1' is non-public and should not be referenced from KDoc comments."
