package sample

/**
 * @param someProp
 */
class MyClass(val someProp: String)

// expect-error 6:7 kdoc-tag-mismatch "Documentation of MyClass is outdated: documented parameters 'someProp' are not present in the declaration"
