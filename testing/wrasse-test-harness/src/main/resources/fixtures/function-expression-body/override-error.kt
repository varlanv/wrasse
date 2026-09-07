package sample

interface Named {
    fun name(): String
}

class Person : Named {
    override fun name(): String {
        return "person"
    }
}

// expect-error 8:33 function-expression-body "Function body should be replaced with body expression"
