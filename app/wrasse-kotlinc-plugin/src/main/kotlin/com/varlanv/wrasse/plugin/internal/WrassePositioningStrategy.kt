package com.varlanv.wrasse.plugin.internal

import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.diagnostics.LightTreePositioningStrategy
import org.jetbrains.kotlin.diagnostics.PositioningStrategies
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategy

val WrassePositioningStrategy: SourceElementPositioningStrategy = SourceElementPositioningStrategy(
    object : LightTreePositioningStrategy() {
        override fun mark(node: LighterASTNode, startOffset: Int, endOffset: Int, tree: FlyweightCapableTreeStructure<LighterASTNode>): List<TextRange> = listOf(
            TextRange(startOffset, endOffset),
        )
    },
    PositioningStrategies.DEFAULT,
)
