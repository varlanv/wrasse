package sample

class Foo {
    fun someStuff(PARAM: String) {
        println(PARAM)
    }
}

// expect-error 4:19 function-parameter-naming "Function parameter name should start with a lowercase letter and use camel case"
