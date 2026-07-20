package sample

class Foo8 {
    open /* keep */ private class Inner
}

// expect-error 4:5 modifier-order "Modifiers out of order, expected: private open (no autofix for this shape)"
