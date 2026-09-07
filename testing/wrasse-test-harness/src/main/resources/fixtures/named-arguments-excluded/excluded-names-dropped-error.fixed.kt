package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

fun demo(): VendorPoint = vendorShift(VendorPoint(1, 2), 3)