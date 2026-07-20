package sample

open class Animal
class Dog :Animal()

fun <T:Any> identity(value: T): T = value

// expect-error 1:1 format "File is not wrasse-formatted"
