package ch.tutteli.atrium.domain.robstoll.lib.creating.throwable.thrown.creators

import ch.tutteli.atrium.assertions.Assertion
import ch.tutteli.atrium.assertions.AssertionGroup
import ch.tutteli.atrium.assertions.ExplanatoryAssertionGroupType
import ch.tutteli.atrium.assertions.builders.invisibleGroup
import ch.tutteli.atrium.core.None
import ch.tutteli.atrium.core.Option
import ch.tutteli.atrium.core.polyfills.fullName
import ch.tutteli.atrium.core.polyfills.stackBacktrace
import ch.tutteli.atrium.creating.Expect
import ch.tutteli.atrium.domain.builders.AssertImpl
import ch.tutteli.atrium.domain.builders.creating.collectors.collectAssertions
import ch.tutteli.atrium.domain.creating.changers.SubjectChanger
import ch.tutteli.atrium.reporting.RawString
import ch.tutteli.atrium.reporting.translating.Translatable
import ch.tutteli.atrium.translations.DescriptionThrowableAssertion

class ThrowableThrownFailureHandler<T : Throwable?, R>(
    private val maxStackTrace: Int
) : SubjectChanger.FailureHandler<T, R> {

    override fun createAssertion(
        originalAssertionContainer: Expect<T>,
        descriptiveAssertion: Assertion,
        maybeAssertionCreator: Option<Expect<R>.() -> Unit>
    ): Assertion {
        val assertions = mutableListOf(descriptiveAssertion)
        maybeAssertionCreator.fold({ /* nothing to do */ }) { assertionCreator ->
            assertions.add(
                AssertImpl.builder.explanatoryGroup
                    .withDefaultType
                    .collectAssertions(None, assertionCreator)
                    .build()
            )
        }
        originalAssertionContainer.maybeSubject.fold(
            { /* nothing to do */ },
            {
                if (it != null) assertions.add(propertiesOfThrowable(it, maxStackTrace))
            }
        )
        return AssertImpl.builder.invisibleGroup
            .withAssertions(assertions.toList())
            .build()
    }

    companion object {

        /**
         * Returns an [AssertionGroup] with an [ExplanatoryAssertionGroupType] containing properties
         * of the given [throwable] where [maxStackTrace] are shown.
         */
        fun propertiesOfThrowable(throwable: Throwable, maxStackTrace: Int): AssertionGroup {
            return AssertImpl.builder.explanatoryGroup
                .withDefaultType
                .withAssertions(
                    AssertImpl.builder.explanatory
                        .withExplanation(
                            DescriptionThrowableAssertion.OCCURRED_EXCEPTION_PROPERTIES,
                            throwable::class.simpleName ?: throwable::class.fullName
                        )
                        .build(),
                    createHints(throwable, maxStackTrace, secondStackFrameOfParent = null)
                )
                .build()
        }


        private fun createHints(
            throwable: Throwable,
            maxStackTrace: Int,
            secondStackFrameOfParent: String?
        ): Assertion {
            val assertions = mutableListOf(
                createMessageHint(throwable),
                createStackTraceHint(
                    throwable,
                    maxStackTrace,
                    secondStackFrameOfParent
                )
            )
            assertions.addAll(createAdditionalHints(throwable, maxStackTrace))
            createCauseHint(throwable, maxStackTrace)?.let { assertions.add(it) }

            return AssertImpl.builder.explanatoryGroup
                .withDefaultType
                .withAssertions(assertions.toList())
                .build()
        }

        private fun createMessageHint(throwable: Throwable) =
            AssertImpl.builder.descriptive
                .holding
                .withDescriptionAndRepresentation(
                    DescriptionThrowableAssertion.OCCURRED_EXCEPTION_MESSAGE,
                    throwable.message
                )
                .build()

        private fun createStackTraceHint(
            throwable: Throwable,
            maxStackTrace: Int,
            secondStackFrameOfParent: String?
        ): Assertion {
            val stackTrace = if (secondStackFrameOfParent != null) {
                throwable.stackBacktrace.takeWhile { it != secondStackFrameOfParent }
            } else {
                throwable.stackBacktrace
            }
            val assertions = stackTrace.asSequence()
                .take(maxStackTrace)
                .map {
                    AssertImpl.builder.explanatory.withExplanation(RawString.create(it)).build()
                }
                .let {
                    if (stackTrace.size > maxStackTrace) {
                        it.plus(AssertImpl.builder.explanatory.withExplanation(RawString.create("...")).build())
                    } else {
                        it
                    }
                }.toList()

            return AssertImpl.builder.list
                .withDescriptionAndEmptyRepresentation(DescriptionThrowableAssertion.OCCURRED_EXCEPTION_STACKTRACE)
                .withAssertions(assertions)
                .build()
        }


        private fun createCauseHint(throwable: Throwable, maxStackTrace: Int): AssertionGroup? =
            throwable.cause?.let { cause ->
                createChildHint(
                    throwable,
                    cause,
                    DescriptionThrowableAssertion.OCCURRED_EXCEPTION_CAUSE,
                    maxStackTrace
                )
            }

        /**
         * Creates a hint for a given [child] of the given [throwable] using the given [childDescription]
         * and displaying [maxStackTrace].
         */
        fun createChildHint(
            throwable: Throwable,
            child: Throwable,
            childDescription: Translatable,
            maxStackTrace: Int
        ): AssertionGroup {
            val secondStackTrace = if (throwable.stackBacktrace.size > 1) throwable.stackBacktrace[1] else null
            return AssertImpl.builder.list
                .withDescriptionAndRepresentation(childDescription, child)
                .withAssertion(
                    createHints(
                        child,
                        maxStackTrace,
                        secondStackTrace
                    )
                )
                .build()
        }
    }
}

/**
 * Hook for platform specific additional hints.
 */
expect fun createAdditionalHints(throwable: Throwable, maxStackTrace: Int): List<Assertion>
