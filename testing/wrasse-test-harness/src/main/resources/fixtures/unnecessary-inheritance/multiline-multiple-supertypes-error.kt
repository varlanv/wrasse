package sample

interface Bar

class Foo :
    Any(),
    Bar

// expect-error 6:5 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
