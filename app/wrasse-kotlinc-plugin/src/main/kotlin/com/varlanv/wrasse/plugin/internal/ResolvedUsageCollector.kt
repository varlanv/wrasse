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
import org.jetbrains.kotlin.fir.FirSession
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
import org.jetbrains.kotlin.fir.originalOrSelf
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirPropertyWithExplicitBackingFieldResolvedNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.references.FirResolvedErrorReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private val CALL_SYNTAX_TYPES = setOf(
    KtNodeTypes.CALL_EXPRESSION,
    KtNodeTypes.DOT_QUALIFIED_EXPRESSION,
    KtNodeTypes.SAFE_ACCESS_EXPRESSION,
)

private class CallAmbiguity(val namingIsAmbiguous: Boolean, val positionalIsAmbiguous: Boolean)

private val NO_AMBIGUITY = CallAmbiguity(namingIsAmbiguous = false, positionalIsAmbiguous = false)

object ResolvedUsageCollector {
    @OptIn(DirectDeclarationsAccess::class)
    fun collect(
        file: FirFile,
        collectQualifiedUsages: Boolean = false,
        collectCallSites: Boolean = false,
    ): WResolvedUsage = runCatching {
        val visitor = UsageVisitor(file, collectQualifiedUsages, collectCallSites)
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
            typeAliases = visitor.typeAliases,
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
        private val file: FirFile,
        private val collectQualifiedUsages: Boolean,
        private val collectCallSites: Boolean,
    ) : FirVisitorVoid() {
        private val session: FirSession = file.moduleData.session
        val classifiers = mutableSetOf<String>()
        val callables = mutableSetOf<WCallableUsage>()
        val qualifiedUsages = mutableListOf<WQualifiedUsage>()
        val callSites = mutableListOf<WCallSite>()
        val typeAliases = mutableMapOf<String, String>()
        var hasErrors = false
        private val scopeSession = ScopeSession()
        private val ambiguityByCallee = HashMap<FirFunctionSymbol<*>, CallAmbiguity>()

        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitFunctionCall(functionCall: FirFunctionCall) {
            if (collectCallSites) {
                runCatching { recordCallSite(functionCall) }
            }
            collectTypeAliasConstructorUsage(functionCall)
            visitElement(functionCall)
        }

        private fun collectTypeAliasConstructorUsage(call: FirFunctionCall) {
            val symbol = (call.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol
            if (symbol !is FirConstructorSymbol) return
            val constructed = call.resolvedType
            if (constructed.abbreviatedType != null) collectConeType(constructed)
        }

        private fun recordCallSite(call: FirFunctionCall) {
            val argumentList = call.argumentList as? FirResolvedArgumentList ?: return
            val callSource = call.source ?: return
            if (callSource.kind !== KtRealSourceElementKind || callSource.elementType !in CALL_SYNTAX_TYPES) return
            val symbol = (call.calleeReference as? FirResolvedNamedReference)?.resolvedSymbol as? FirFunctionSymbol<*>
                ?: return
            val parameterSymbols = symbol.valueParameterSymbols
            val namedSpans = namedArgumentSpans(argumentList)
            val arguments = ArrayList<WCallArgument>()
            for ((expression, parameter) in argumentList.mapping) {
                val parameterName = parameter.name.asString()
                val parameterIndex = parameterSymbols.indexOf(parameter.symbol)
                if (expression is FirVarargArgumentsExpression) {
                    for (element in expression.arguments) addArgument(
                        arguments,
                        element,
                        parameterName,
                        isVararg = true,
                        parameterIndex,
                        namedSpans,
                    )
                } else {
                    addArgument(arguments, expression, parameterName, parameter.isVararg, parameterIndex, namedSpans)
                }
            }
            val callableId = symbol.callableId
            val ambiguity = ambiguityOf(symbol, callableId)
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
                    parameterCount = parameterSymbols.size,
                    namingIsAmbiguous = ambiguity.namingIsAmbiguous,
                    positionalIsAmbiguous = ambiguity.positionalIsAmbiguous,
                ),
            )
        }

        private fun ambiguityOf(symbol: FirFunctionSymbol<*>, callableId: CallableId): CallAmbiguity {
            val original = symbol.originalOrSelf()
            return ambiguityByCallee.getOrPut(original) { computeAmbiguity(original, symbol, callableId) }
        }

        private fun computeAmbiguity(
            original: FirFunctionSymbol<*>,
            symbol: FirFunctionSymbol<*>,
            callableId: CallableId,
        ): CallAmbiguity {
            val calleeParameterNames = original.valueParameterSymbols.map { it.name.asString() }.toSet()
            val calleeParameterCount = original.valueParameterSymbols.size
            val calleeReceiverClassId = receiverClassId(original)
            val classId = callableId.classId
            val candidates: List<FirFunctionSymbol<*>> = when {
                symbol is FirConstructorSymbol -> {
                    val ownerClassId = classId ?: return NO_AMBIGUITY
                    val classSymbol = session.symbolProvider.getClassLikeSymbolByClassId(
                        ownerClassId,
                    ) as? FirClassSymbol<*> ?: return NO_AMBIGUITY
                    classSymbol
                        .unsubstitutedScope(
                            session,
                            scopeSession,
                            withForcedTypeCalculator = false,
                            memberRequiredPhase = null,
                        )
                        .getDeclaredConstructors()
                }

                classId == null -> topLevelCandidates(callableId)

                else -> {
                    val classSymbol = session.symbolProvider.getClassLikeSymbolByClassId(
                        classId,
                    ) as? FirClassSymbol<*> ?: return NO_AMBIGUITY
                    classSymbol
                        .unsubstitutedScope(
                            session,
                            scopeSession,
                            withForcedTypeCalculator = false,
                            memberRequiredPhase = null,
                        )
                        .getFunctions(callableId.callableName)
                }
            }
            var namingIsAmbiguous = false
            var positionalIsAmbiguous = false
            for (candidate in candidates) {
                val candidateOriginal = candidate.originalOrSelf()
                if (candidateOriginal === original) continue
                if (receiverClassId(candidateOriginal) != calleeReceiverClassId) continue
                if (candidateOriginal.valueParameterSymbols
                    .map { it.name.asString() }
                    .toSet() == calleeParameterNames) {
                    namingIsAmbiguous = true
                }
                if (candidateOriginal.valueParameterSymbols.size == calleeParameterCount) {
                    positionalIsAmbiguous = true
                }
                if (namingIsAmbiguous && positionalIsAmbiguous) break
            }
            return CallAmbiguity(namingIsAmbiguous, positionalIsAmbiguous)
        }

        private fun topLevelCandidates(callableId: CallableId): List<FirFunctionSymbol<*>> {
            val packages = LinkedHashSet<FqName>()
            packages.add(callableId.packageName)
            packages.add(file.packageDirective.packageFqName)
            for (import in file.imports) {
                val resolved = import as? FirResolvedImport ?: continue
                if (resolved.isAllUnder) {
                    packages.add(resolved.packageFqName)
                } else if (resolved.resolvedParentClassId == null && resolved.importedName == callableId.callableName) {
                    packages.add(resolved.packageFqName)
                }
            }
            val candidates = ArrayList<FirFunctionSymbol<*>>()
            for (pkg in packages) {
                candidates.addAll(session.symbolProvider.getTopLevelFunctionSymbols(pkg, callableId.callableName))
            }
            return candidates
        }

        private fun receiverClassId(symbol: FirFunctionSymbol<*>): ClassId? =
            (symbol.resolvedReceiverTypeRef?.coneType as? ConeClassLikeType)?.lookupTag?.classId

        private fun addArgument(
            out: MutableList<WCallArgument>,
            expression: FirExpression,
            parameterName: String,
            isVararg: Boolean,
            parameterIndex: Int,
            namedSpans: Set<Long>,
        ) {
            val value = when (expression) {
                is FirNamedArgumentExpression -> expression.expression
                is FirSpreadArgumentExpression -> expression.expression
                else -> expression
            }
            val source = value.source ?: expression.source ?: return
            if (source.startOffset < 0 || source.endOffset < source.startOffset) return
            val isNamed = spanKey(source.startOffset, source.endOffset) in namedSpans
            out.add(
                WCallArgument(source.startOffset, source.endOffset, parameterName, isVararg, parameterIndex, isNamed),
            )
        }

        /**
         * FIR discards [FirNamedArgumentExpression] wrappers while completing a call ([argumentList]'s
         * own `mapping` never contains one), so whether an argument was written named has to come from
         * [FirResolvedArgumentList.originalArgumentList] instead: the span of each named wrapper's own
         * (innermost, past any spread) value expression.
         */
        private fun namedArgumentSpans(argumentList: FirResolvedArgumentList): Set<Long> {
            val original = argumentList.originalArgumentList ?: return emptySet()
            var spans: MutableSet<Long>? = null
            for (argument in original.arguments) {
                if (argument !is FirNamedArgumentExpression) continue
                val source = innermostExpression(argument.expression).source ?: continue
                if (source.startOffset < 0 || source.endOffset < source.startOffset) continue
                if (spans == null) spans = HashSet()
                spans.add(spanKey(source.startOffset, source.endOffset))
            }
            return spans ?: emptySet()
        }

        private fun innermostExpression(expression: FirExpression): FirExpression =
            if (expression is FirSpreadArgumentExpression) innermostExpression(expression.expression) else expression

        private fun spanKey(start: Int, end: Int): Long = (start.toLong() shl 32) or (end.toLong() and 0xFFFF_FFFFL)

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
                val aliasFqName = symbol.classId.asFqNameString()
                classifiers.add(aliasFqName)
                (symbol.resolvedExpandedTypeRef.coneType as? ConeClassLikeType)?.let {
                    typeAliases[aliasFqName] = it.lookupTag.classId.asFqNameString()
                }
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
            val abbreviated = coneType.abbreviatedType
            if (abbreviated != null) {
                collectConeType(abbreviated)
                if (abbreviated is ConeClassLikeType && coneType is ConeClassLikeType) {
                    typeAliases[abbreviated.lookupTag.classId.asFqNameString()] =
                        coneType.lookupTag.classId.asFqNameString()
                }
            }
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
