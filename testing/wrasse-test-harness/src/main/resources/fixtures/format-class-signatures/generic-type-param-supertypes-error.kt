package sample

interface Sup1

interface Sup2

class Box<T>(a: T, b: T, c: T) : Sup1, Sup2

// expect-error 1:1 format "File is not wrasse-formatted"
