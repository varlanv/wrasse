package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

fun demo(): VendorPoint = vendorShift(VendorPoint(x = 1, y = 2), dx = 3)

// fixture-aux-file: aux/Vendor.kt
// expect-error 6:38 no-mixed-named-positional-arguments "Named and positional arguments must not be mixed in one call (no autofix for this shape)"
