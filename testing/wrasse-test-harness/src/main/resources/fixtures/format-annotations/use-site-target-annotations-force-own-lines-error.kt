package sample

annotation class A1

annotation class A2

class Holder {
    @field:A1 @get:A2 var x: Int = 0
}

// expect-error 1:1 format "File is not wrasse-formatted"
