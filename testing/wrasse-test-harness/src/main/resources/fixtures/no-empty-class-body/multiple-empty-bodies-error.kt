package sample

class Foo {}

interface Bar {}

// expect-error 3:11 no-empty-class-body "Empty class body"
// expect-error 5:15 no-empty-class-body "Empty class body"
