package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

fun demo(): VendorPoint = vendorShift(point = VendorPoint(x = 1, y = 2), dx = 3)

// fixture-aux-file: aux/Vendor.kt
// expect-error 6:38 named-arguments "Arguments of a callee in an excluded package should be positional"
// expect-error 6:58 named-arguments "Arguments of a callee in an excluded package should be positional"
