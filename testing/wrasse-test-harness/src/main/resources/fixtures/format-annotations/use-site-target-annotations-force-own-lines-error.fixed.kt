package sample

annotation class A1

annotation class A2

class Holder {
    @field:A1
    @get:A2
    var x: Int = 0
}