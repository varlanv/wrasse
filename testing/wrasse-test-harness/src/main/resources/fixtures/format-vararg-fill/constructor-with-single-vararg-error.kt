package sample

class Bag(vararg val items: String)

val bag = Bag("alpha", "beta", "gamma", "delta", "epsilon", "zeta")

// expect-error 1:1 format "File is not wrasse-formatted"
