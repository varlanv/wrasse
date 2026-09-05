package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

class Wrapper(val point: VendorPoint, val label: String)

fun demo(): Wrapper = Wrapper(vendorShift(VendorPoint(1, 2), 3), "a")

// fixture-aux-file: aux/Vendor.kt
// expect-error 8:30 named-arguments "Positional arguments should be named"
