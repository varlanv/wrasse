package sample

interface Consumer< T > {
    fun add(item: T)
}

// expect-error 1:1 format "File is not wrasse-formatted"
