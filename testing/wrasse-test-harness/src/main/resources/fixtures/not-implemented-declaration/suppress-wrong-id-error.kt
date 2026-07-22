package sample

@Suppress("no-semicolons")
fun foo() {
    TODO()
}

// expect-error 5:5 not-implemented-declaration "The NotImplementedDeclaration should only be used when a method stub is necessary. This defers the development of the functionality of this function. Hence, the NotImplementedDeclaration should only serve as a temporary declaration. Before releasing, this type of declaration should be removed."
