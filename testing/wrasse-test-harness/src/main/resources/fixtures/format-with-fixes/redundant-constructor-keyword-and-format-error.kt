package sample

class Box constructor(val name: String)

// expect-error 3:11 redundant-constructor-keyword "Redundant constructor keyword"
// expect-error 1:1 format "File is not wrasse-formatted"
