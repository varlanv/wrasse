package sample

/**
 * Comment
 * [prop1] - non-public property
 */
@Suppress("kdoc-references-non-public-property")
class Test {
    private val prop1 = 0
}

// expect-clean
