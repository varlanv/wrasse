package sample

import sample.vendor.vendorFind

fun demo(): Int = vendorFind(text = ".", ignoreCase = false) + vendorFind(".")