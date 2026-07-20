package sample

class Box<
    T,
    R
>(val first: T, val second: R)

// expect-error 1:1 format "File is not wrasse-formatted"
