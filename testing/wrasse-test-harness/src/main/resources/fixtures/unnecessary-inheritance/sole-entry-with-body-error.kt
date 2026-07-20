package sample

class Foo : Any() {}

// expect-error 3:13 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
