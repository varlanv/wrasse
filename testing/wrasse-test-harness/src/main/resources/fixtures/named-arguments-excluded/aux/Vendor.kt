package sample.vendor

class VendorPoint(val x: Int, val y: Int)

fun vendorShift(point: VendorPoint, dx: Int): VendorPoint = VendorPoint(point.x + dx, point.y)
