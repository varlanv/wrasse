package sample


import kotlin.math.PI

fun area(radius: Double): Double = PI * radius * radius

// expect-error 1:1 format "File is not wrasse-formatted"
