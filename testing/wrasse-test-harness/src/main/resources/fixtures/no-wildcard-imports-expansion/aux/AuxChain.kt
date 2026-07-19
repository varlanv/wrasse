package sample.auxchain

class A {
    val b: B = B()
    val items: List<C> = listOf(C())
}

class B {
    fun doSomething(): Int = 1
}

class C {
    fun compute(): Int = 2
}
