package sample

interface Bar

class Foo : Any() /* c */, Bar

// expect-error 5:13 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
