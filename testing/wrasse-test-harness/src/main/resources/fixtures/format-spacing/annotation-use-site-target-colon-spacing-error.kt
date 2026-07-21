package sample

class Holder {
    @field :JvmField
    val value: Int = 0
}

// expect-error 1:1 format "File is not wrasse-formatted"
