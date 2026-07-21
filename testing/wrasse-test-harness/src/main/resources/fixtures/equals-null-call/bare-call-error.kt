package sample

class Sample {
    override fun equals(other: Any?): Boolean = equals(null)

    override fun hashCode(): Int = 0
}

// expect-error 4:49 equals-null-call "Calling equals() with null as the argument; use '==' to compare with null instead"
