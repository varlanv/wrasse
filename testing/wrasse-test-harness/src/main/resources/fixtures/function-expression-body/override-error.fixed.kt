package sample

interface Named {
    fun name(): String
}

class Person : Named {
    override fun name(): String = "person"
}