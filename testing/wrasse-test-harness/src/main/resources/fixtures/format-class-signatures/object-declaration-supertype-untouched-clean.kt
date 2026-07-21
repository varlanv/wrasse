package sample

interface Sup1

interface Sup2

object Config : Sup1, Sup2

// expect-clean
