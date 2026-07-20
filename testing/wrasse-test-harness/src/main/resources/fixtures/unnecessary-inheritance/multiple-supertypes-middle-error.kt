package sample

interface Bar
interface Baz

class Foo : Bar, Any(), Baz

// expect-error 6:18 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
