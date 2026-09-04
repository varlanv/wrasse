package sample

abstract class BaseSpec(block: BaseSpec.() -> Unit) {
    init {
        block()
    }

    fun should(name: String, test: () -> Unit) {
        test()
    }
}

class FooSpec :
    BaseSpec(
        {

            should("test something") {
                println("hello")
            }

            should("test another") {
                println("world")
            }
        },
    )

// expect-error 1:1 format "File is not wrasse-formatted"
