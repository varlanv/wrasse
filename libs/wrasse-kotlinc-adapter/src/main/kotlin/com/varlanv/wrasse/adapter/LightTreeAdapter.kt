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
        val sourceText = source.getElementTextInContextForDebug()
        val rootNode = buildTree(tree, sourceText)
        return WFile(filePath, rootNode, sourceText)
    }

    private fun buildTree(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        sourceText: CharSequence,
    ): WNode {
        val root = tree.root
        val rootWNode = toWNode(root, sourceText)
        val stack = ArrayDeque<Pair<LighterASTNode, WNode>>()
        stack.addLast(root to rootWNode)
        val ref = Ref<Array<LighterASTNode?>>()

        while (stack.isNotEmpty()) {
            val (astNode, wNode) = stack.removeLast()
            val count = tree.getChildren(astNode, ref)
            val childArray = ref.get() ?: continue
            if (count <= 0) continue

            val children = ArrayList<WNode>(count)
            for (i in 0 until count) {
                val child = childArray[i] ?: continue
                val childWNode = toWNode(child, sourceText)
                childWNode.parent = wNode
                children.add(childWNode)
                if (child !is LighterASTTokenNode) {
                    stack.addLast(child to childWNode)
                }
            }
            wNode.children = children
        }

        return rootWNode
    }

    private fun toWNode(node: LighterASTNode, sourceText: CharSequence): WNode {
        val isLeaf = node is LighterASTTokenNode
        return WNode(
            type = WNodeTypeMapping.map(node.tokenType),
            startOffset = node.startOffset,
            endOffset = node.endOffset,
            leafText = if (isLeaf) node.text else null,
            sourceText = sourceText,
        )
    }
}
