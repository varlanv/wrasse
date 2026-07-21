package sample

interface VeryLongSuperTypeNameIndeedHere

class Foo : VeryLongSuperTypeNameIndeedHere

// expect-error 1:1 format "File is not wrasse-formatted"
