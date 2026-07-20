package sample

class Foo14 {
    tailrec protected fun bar(s: String): String = bar(s.substringBeforeLast("\n"))
}

// expect-error 4:5 modifier-order "Modifiers out of order, expected: protected tailrec"
