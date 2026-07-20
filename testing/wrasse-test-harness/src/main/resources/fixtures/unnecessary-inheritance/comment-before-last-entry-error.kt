package sample

interface Bar

class Foo : Bar, /* c */ Any()

// expect-error 5:26 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
