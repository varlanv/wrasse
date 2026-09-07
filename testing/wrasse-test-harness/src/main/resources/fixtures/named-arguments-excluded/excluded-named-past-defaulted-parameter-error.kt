package sample

import sample.vendor.vendorFind

fun demo(): Int = vendorFind(text = ".", ignoreCase = false) + vendorFind(text = ".")

// fixture-aux-file: aux/Vendor.kt
// expect-error 5:74 named-arguments "Arguments of a callee in an excluded package should be positional"
