package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

fun demo(): VendorPoint = vendorShift(dx = 3, point = VendorPoint(1, 2))

// fixture-aux-file: aux/Vendor.kt
// expect-clean
