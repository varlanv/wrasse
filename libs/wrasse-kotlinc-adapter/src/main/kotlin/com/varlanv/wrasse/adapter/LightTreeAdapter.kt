package com.varlanv.wrasse.adapter

import com.varlanv.wrasse.model.WFile
import com.varlanv.wrasse.model.WNode
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.LighterASTTokenNode
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure

object LightTreeAdapter {

    fun adapt(source: KtLightSourceElement, filePath: String): WFile {
        val tree = source.treeStructure
        val root = tree.root
        val sourceText = source.getElementTextInContextForDebug()
        val rootNode = buildNode(tree, root, sourceText)
        return WFile(filePath, rootNode, sourceText)
    }

    private fun buildNode(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        node: LighterASTNode,
        sourceText: CharSequence,
    ): WNode {
        val type = WNodeTypeMapping.map(node.tokenType)
        val isLeaf = node is LighterASTTokenNode
        val leafText = if (isLeaf) node.text else null

        val wNode = WNode(
            type = type,
            startOffset = node.startOffset,
            endOffset = node.endOffset,
            leafText = leafText,
            sourceText = sourceText,
        )

        if (!isLeaf) {
            val ref = Ref<Array<LighterASTNode?>>()
            val count = tree.getChildren(node, ref)
            val childArray = ref.get()
            if (childArray != null && count > 0) {
                val children = ArrayList<WNode>(count)
                for (i in 0 until count) {
                    val child = childArray[i] ?: continue
                    val childNode = buildNode(tree, child, sourceText)
                    childNode.parent = wNode
                    children.add(childNode)
                }
                wNode.children = children
            }
        }

        return wNode
    }
}
