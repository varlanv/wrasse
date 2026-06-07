package com.varlanv.wrasse.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class WrasseIrVisitor(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private val printlnFun by lazy {
        @Suppress("DEPRECATION")
        pluginContext.referenceFunctions(
            CallableId(FqName("kotlin.io"), Name.identifier("println"))
        ).first {
            it.owner.parameters.size == 1 && it.owner.parameters[0].type == pluginContext.irBuiltIns.anyNType
        }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        val body = declaration.body
        if (body is IrBlockBody && declaration.kotlinFqName.asString().contains("sample")) {
            val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
            val printlnCall = builder.irCall(printlnFun).apply {
                arguments[0] = builder.irString("[wrasse] entering ${declaration.kotlinFqName}")
            }
            body.statements.add(0, printlnCall)
        }
        return super.visitFunction(declaration)
    }
}
