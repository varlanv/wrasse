package sample

import sample.vendor.VendorPoint
import sample.vendor.vendorShift

class Wrapper(val point: VendorPoint, val label: String)

fun demo(): Wrapper = Wrapper(point = vendorShift(VendorPoint(1, 2), 3), label = "a")