package com.sidr.launcher.domain.action

/**
 * How much friction a registered action needs before it runs (drives confirmation in AIL-5).
 *
 * - [SAFE] — execute directly (e.g. launch an installed app, show the app grid).
 * - [CONFIRM] — ask first (e.g. open an arbitrary URL, open the Play Store, or **any** LLM-proposed
 *   action: per the AIL-4 decision a router proposal never auto-executes).
 * - [DANGEROUS] — reserved for Stage 3 (irreversible / automation). Not producible in the MVP.
 *
 * The MVP catalog (AIL-2) uses only [SAFE] and [CONFIRM]; [DANGEROUS] is defined so the risk model is
 * complete and the confirmation `when` in AIL-5 is total.
 */
enum class ActionRiskLevel {
    SAFE,
    CONFIRM,
    DANGEROUS,
}
