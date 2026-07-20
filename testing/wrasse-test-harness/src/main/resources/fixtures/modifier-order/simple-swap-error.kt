package sample

suspend public fun returnsSomething(): String = ""

// expect-error 3:1 modifier-order "Modifiers out of order, expected: public suspend"
