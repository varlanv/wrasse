package sample

interface Sup1

interface Sup2

class Single : /* one */  Sup1

class Wide : /* wide
 note */  Sup1

class Both : /* one */  Sup1, Sup2

// expect-error 1:1 format "File is not wrasse-formatted"
