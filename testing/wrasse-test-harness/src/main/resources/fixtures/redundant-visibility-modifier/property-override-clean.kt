package sample

abstract class Base {
    abstract val test: String
}

class Foo : Base() {
    public override val test: String = "value"
}

// expect-clean
