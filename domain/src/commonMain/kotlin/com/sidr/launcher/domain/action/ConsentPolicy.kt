package com.sidr.launcher.domain.action

/**
 * **The single point where risk becomes a gate** (`DOC-ADL-1`): the same risk gets the same gate
 * regardless of who proposed the action — a rule, the model, the agent, automation, or direct UI.
 *
 * Four sites decided this independently before A1′, in two spellings that coincide only because there
 * are exactly three levels: `risk >= CONFIRM` (agent) and `risk != SAFE` (model, learned memory, UI).
 * Insert a level below `CONFIRM` and three of the four gate it while the agent does not. That is why
 * this function exists, and why it is written against **the lowest level** rather than against a named
 * one: a level added below `SAFE` must gate too.
 *
 * What unifies is the **predicate**, not the outcome. Each caller still answers its own question —
 * a consent checkpoint, a confirm card, an auto-resolve permission, a display state — and merging
 * those four return types would be forcing, not unifying.
 */
fun requiresConsent(risk: ActionRiskLevel): Boolean = risk != ActionRiskLevel.entries.first()
