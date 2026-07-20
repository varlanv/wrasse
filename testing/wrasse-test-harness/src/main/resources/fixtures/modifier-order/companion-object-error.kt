package sample

class Foo3 {
    final internal companion object {}
}

// expect-error 4:5 modifier-order "Modifiers out of order, expected: internal final companion"
