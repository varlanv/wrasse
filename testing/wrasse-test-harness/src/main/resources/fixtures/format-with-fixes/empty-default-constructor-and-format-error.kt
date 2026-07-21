package sample

class Box() {
    val name = "value"
}

// expect-error 3:10 empty-default-constructor "Empty default constructor"
// expect-error 1:1 format "File is not wrasse-formatted"
