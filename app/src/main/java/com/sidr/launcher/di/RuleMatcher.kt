package com.sidr.launcher.di

import javax.inject.Qualifier

/**
 * Qualifies the deterministic rule-based [com.sidr.launcher.domain.intent.IntentMatcher]
 * (`RuleBasedIntentMatcher`) — the **primary** in Block R's `LayeredIntentMatcher`.
 *
 * Tagged rather than bound as the unqualified `IntentMatcher` so the rule-first composite can take
 * the unqualified slot while composing this rule matcher with the [NluMatcher]. Mirrors the
 * [CloudEngine]/[FallbackEngine] → `GenerativeRouter` precedent.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RuleMatcher
