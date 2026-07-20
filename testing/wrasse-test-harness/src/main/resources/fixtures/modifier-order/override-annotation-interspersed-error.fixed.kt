package sample

annotation class Ann18
annotation class Woohoo18(val data: String)

open class Base18 {
    open suspend fun doSomething(): String = ""
    open fun getSomething(): String = ""
}

class Foo18 : Base18() {
    override @Ann18 fun getSomething() = ""
    public @Ann18 override suspend @Woohoo18(data = "woohoo") fun doSomething() = ""
}