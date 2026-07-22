package sample

class MyClass {
    /**
     * @param someParam
     */
    constructor(otherParam: String)
}

// expect-error 7:5 kdoc-tag-mismatch "Documentation of MyClass is outdated: documented parameters 'someParam' are not present in the declaration"
