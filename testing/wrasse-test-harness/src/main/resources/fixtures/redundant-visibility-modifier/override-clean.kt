package sample

abstract class Base {
    abstract fun foo()
}

class Foo : Base() {
    public override fun foo() {}
}

// expect-clean
