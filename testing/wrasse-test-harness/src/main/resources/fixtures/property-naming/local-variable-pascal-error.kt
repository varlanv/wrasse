package sample

fun foo() {
    val LocalBad = 1
}

// expect-error 4:9 property-naming "Property name should start with a lowercase letter and use camel case"
