package com.varlanv.wrasse.plugin.internal

import com.varlanv.wrasse.model.WCallArgument
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WQualifiedUsageKind
import com.varlanv.wrasse.model.WResolvedImport
import com.varlanv.wrasse.model.WResolvedUsage
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.FirErrorResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirPropertyWithExplicitBackingFieldResolvedNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.references.FirResolvedErrorReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

private val CALL_SYNTAX_TYPES = setOf(
    KtNodeTypes.CALL_EXPRESSION,
    KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
    KtNodeTypes.SAFE_ACCESS_EXPRESSION,
)

object ResolvedUsageCollector {
    @OptIn(DirectDeclarationsAccess::class)
    fun collect(
        file: FirFile,
        collectQualifiedUsages: Boolean = false,
        collectCallSites: Boolean = false,
    ): WResolvedUsage = runCatching {
        val visitor = UsageVisitor(collectQualifiedUsages, collectCallSites)
        for (annotation in file.annotations) {
            annotation.accept(visitor)
        }
        for (declaration in file.declarations) {
            declaration.accept(visitor)
        }
        WResolvedUsage(
            classifiers = visitor.classifiers,
            callables = visitor.callables,
            hasResolutionErrors = visitor.hasErrors,
            resolvedImports = collectResolvedImports(file),
            qualifiedUsages = visitor.qualifiedUsages,
            callSites = visitor.callSites,
        )
    }.getOrElse {
        WResolvedUsage(
            classifiers = emptySet(),
            callables = emptySet(),
            hasResolutionErrors = true,
            resolvedImports = emptyList(),
            qualifiedUsages = emptyList(),
        )
    }

    private fun collectResolvedImports(file: FirFile): List<WResolvedImport> = file.imports.mapNotNull { import ->
        val fqn = import.importedFqName?.takeUnless { it.isRoot }?.asString() ?: return@mapNotNull null
        if (import is FirResolvedImport) {
            WResolvedImport(
                fqn = fqn,
                isStarImport = import.isAllUnder,
                resolvedParentClassFqName = import.resolvedParentClassId?.asFqNameString(),
                resolved = true,
            )
        } else {
            WResolvedImport(
                fqn = fqn,
                isStarImport = import.isAllUnder,
                resolvedParentClassFqName = null,
                resolved = false,
            )
        }
    }

    private class UsageVisitor(
        private val collectQualifiedUsages: Boolean,
        private val collectCallSites: Boolean,
    ) : FirVisitorVoid() {
        val classifiers = mutableSetOf<String>()
        val callables = mutableSetOf<WCallableUsage>()
        val qualifiedUsages = mutableListOf<WQualifiedUsage>()
        val callSites = mutableListOf<WCallSite>()
        var hasErrors = false

        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitFunctionCall(functionCall: FirFunctionCall) {
            if (collectCallSites) {
                runCatching { recordCallSite(functionCall) }
            }
            visitElement(functionCall)
        }

        private fun recordCallSite(call: FirFunctionCall) {
            val argumentList = call.argumentList as? FirResolvedArgumentList ?: return
            val callSource = call.source ?: return
            if (callSource.kind !== KtRealSourceElementKind || callSource.elementType !in CALL_SYNTAX_TYPES) return
            val symbol = (call.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol as? FirFunctionSymbol<*>
                ?: return
            val arguments = ArrayList<WCallArgument>()
            for ((expression, parameter) in argumentList.mapping) {
                val parameterName = parameter.name.asString()
                if (expression is FirVarargArgumentsExpression) {
                    for (element in expression.arguments) addArgument(
                        arguments,
                        element,
                        parameterName,
                        isVararg = true,
                    )
                } else {
                    addArgument(arguments, expression, parameterName, parameter.isVararg)
                }
            }
            val callableId = symbol.callableId
            callSites.add(
                WCallSite(
                    callStartOffset = callSource.startOffset,
                    callEndOffset = callSource.endOffset,
                    calleePackageFqName = callableId.packageName.asString(),
                    calleeClassFqName = callableId.classId?.asFqNameString(),
                    calleeName = callableId.callableName.asString(),
                    hasStableParameterNames = symbol.resolvedStatus.hasStableParameterNames,
                    arguments = arguments,
                    isConstructor = symbol is FirConstructorSymbol,
                    parameterCount = symbol.valueParameterSymbols.size,
                ),
            )
        }

        private fun addArgument(
            out: MutableList<WCallArgument>,
            expression: FirExpression,
            parameterName: String,
            isVararg: Boolean,
        ) {
            val value = when (expression) {
                is FirNamedArgumentExpression -> expression.expression
                is FirSpreadArgumentExpression -> expression.expression
                else -> expression
            }
            val source = value.source ?: expression.source ?: return
            if (source.startOffset < 0 || source.endOffset < source.startOffset) return
            out.add(WCallArgument(source.startOffset, source.endOffset, parameterName, isVararg))
        }

        override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
            collectCallableUsage(resolvedNamedReference)
            visitElement(resolvedNamedReference)
        }

        override fun visitResolvedCallableReference(resolvedCallableReference: FirResolvedCallableReference) {
            collectCallableUsage(resolvedCallableReference)
            visitElement(resolvedCallableReference)
        }

        override fun visitPropertyWithExplicitBackingFieldResolvedNamedReference(
            propertyWithExplicitBackingFieldResolvedNamedReference: FirPropertyWithExplicitBackingFieldResolvedNamedReference,
        ) {
            collectCallableUsage(propertyWithExplicitBackingFieldResolvedNamedReference)
            visitElement(propertyWithExplicitBackingFieldResolvedNamedReference)
        }

        override fun visitResolvedErrorReference(resolvedErrorReference: FirResolvedErrorReference) {
            hasErrors = true
            visitElement(resolvedErrorReference)
        }

        override fun visitErrorNamedReference(errorNamedReference: FirErrorNamedReference) {
            hasErrors = true
            visitElement(errorNamedReference)
        }

        private fun collectCallableUsage(resolvedNamedReference: FirResolvedNamedReference) {
            val symbol = resolvedNamedReference.resolvedSymbol
            if (symbol is FirCallableSymbol<*>) {
                val callableId = symbol.callableId
                if (callableId != null && callableId.packageName != CallableId.PACKAGE_FQ_NAME_FOR_LOCAL) {
                    callables.add(toCallableUsage(callableId, symbol.isStatic))
                }
            }
        }

        override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
            collectConeType(resolvedTypeRef.coneType)
            if (collectQualifiedUsages) {
                recordTypeRefUsage(resolvedTypeRef)
            }
            visitElement(resolvedTypeRef)
        }

        override fun visitErrorTypeRef(errorTypeRef: FirErrorTypeRef) {
            hasErrors = true
            visitElement(errorTypeRef)
        }

        override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
            resolvedQualifier.classId?.let { classifiers.add(it.asFqNameString()) }
            val symbol = resolvedQualifier.symbol
            if (symbol is FirTypeAliasSymbol) {
                classifiers.add(symbol.classId.asFqNameString())
            }
            if (collectQualifiedUsages) {
                recordQualifierUsage(resolvedQualifier)
            }
            visitElement(resolvedQualifier)
        }

        override fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier) {
            hasErrors = true
            visitElement(errorResolvedQualifier)
        }

        private fun collectConeType(coneType: ConeKotlinType) {
            coneType.abbreviatedType?.let { collectConeType(it) }
            when (coneType) {
                is ConeErrorType -> hasErrors = true
                is ConeClassLikeType -> {
                    classifiers.add(coneType.lookupTag.classId.asFqNameString())
                    for (typeArgument in coneType.typeArguments) {
                        typeArgument.type?.let { collectConeType(it) }
                    }
                }

                else -> {
                    for (typeArgument in coneType.typeArguments) {
                        typeArgument.type?.let { collectConeType(it) }
                    }
                }
            }
        }

        private fun recordQualifierUsage(resolvedQualifier: FirResolvedQualifier) {
            val classId = resolvedQualifier.classId ?: return
            recordUsage(resolvedQualifier.source, classId, WQualifiedUsageKind.QUALIFIER)
        }

        private fun recordTypeRefUsage(resolvedTypeRef: FirResolvedTypeRef) {
            val coneType = resolvedTypeRef.coneType
            val writtenType = coneType.abbreviatedType ?: coneType
            val classId = (writtenType as? ConeClassLikeType)?.lookupTag?.classId ?: return
            recordUsage(resolvedTypeRef.source, classId, WQualifiedUsageKind.TYPE_REF)
        }

        private fun recordUsage(
            source: KtSourceElement?,
            classId: ClassId,
            kind: WQualifiedUsageKind,
        ) {
            if (source == null || source.kind !== KtRealSourceElementKind) return
            val start = source.startOffset
            val end = source.endOffset
            if (start < 0 || end < start) return
            val packageFqName = if (classId.packageFqName.isRoot) "" else classId.packageFqName.asString()
            qualifiedUsages.add(WQualifiedUsage(start, end, classId.asFqNameString(), packageFqName, kind))
        }

        private fun toCallableUsage(callableId: CallableId, isStatic: Boolean): WCallableUsage {
            val classFqName = callableId.classId?.asFqNameString()
            return WCallableUsage(
                packageFqName = callableId.packageName.asString(),
                classFqName = classFqName,
                name = callableId.callableName.asString(),
                isStatic = isStatic,
            )
        }
    }
}
