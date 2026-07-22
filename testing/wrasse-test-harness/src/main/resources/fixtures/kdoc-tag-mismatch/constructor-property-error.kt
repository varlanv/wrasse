package sample

/**
 * @param someParam
 * @property someProp
 */
class MyClass(otherParam: String, val otherProp: String)

// expect-error 7:7 kdoc-tag-mismatch "Documentation of MyClass is outdated: documented parameters 'someParam', 'someProp' are not present in the declaration"
