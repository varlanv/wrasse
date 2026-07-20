package sample

import java.lang.Object

class Foo : Object() {}

// expect-error 5:13 unnecessary-inheritance "Unnecessary inheritance of 'Object'"
