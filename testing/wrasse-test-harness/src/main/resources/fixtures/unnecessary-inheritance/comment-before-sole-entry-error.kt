package sample

class Foo : /* c */ Any() {}

// expect-error 3:21 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
