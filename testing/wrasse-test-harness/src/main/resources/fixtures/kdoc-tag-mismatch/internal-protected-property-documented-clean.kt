package sample

/**
 * @property a desc
 * @property b desc
 */
open class MyClass(
    internal val a: String,
    protected val b: String,
)

// expect-clean
