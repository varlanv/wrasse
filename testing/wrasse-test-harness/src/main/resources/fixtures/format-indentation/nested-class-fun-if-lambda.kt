package sample

class Greeter {
fun greet(names: List<String>) {
if (names.isEmpty()) {
println("nobody")
} else {
names.forEach { name ->
println("hello, " + name)
}
}
}
}

// expect-error 1:1 format "File is not wrasse-formatted"
