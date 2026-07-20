package sample

interface Bar

class Foo : Bar, Any()

// expect-error 5:18 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
