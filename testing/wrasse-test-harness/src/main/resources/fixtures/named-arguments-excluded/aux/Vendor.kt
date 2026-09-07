package sample.vendor

class VendorPoint(val x: Int, val y: Int)

fun vendorShift(point: VendorPoint, dx: Int): VendorPoint = VendorPoint(point.x + dx, point.y)

fun vendorFind(text: String, startIndex: Int = 0, ignoreCase: Boolean = false): Int = text.length + startIndex + ignoreCase.hashCode()
