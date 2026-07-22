package sample

class Holder {
    val value: Int by lazy {
        System.getProperty("some.prop")?.let {
            return@lazy it.length
        }
        42
    }
}

// expect-clean
