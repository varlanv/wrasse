package sample

/**
 * [peek] - links the public function below, not the private property of the same name
 */
class Test {
    private var peek: Int = 0

    fun peek() = 0
}

// expect-clean
