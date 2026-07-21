package sample

class SampleClass {
    private val a = "ok" // ok
    private val b = "not ok" // false positive
}

// expect-clean
