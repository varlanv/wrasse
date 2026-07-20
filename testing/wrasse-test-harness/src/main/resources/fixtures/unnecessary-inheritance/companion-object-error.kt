package sample

class Container {
    companion object : Any() {
        val x = 1
    }
}

// expect-error 4:24 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
