package sample

class Foo2</* raw */ T>

// expect-error 3:12 type-parameter-comment "A comment in a type parameter list is only allowed when placed on a separate line"
