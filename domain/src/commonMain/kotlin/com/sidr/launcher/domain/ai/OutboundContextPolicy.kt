package com.sidr.launcher.domain.ai

/**
 * The explicit **positive allow-list** + denylists that make Sidr's outbound privacy posture
 * **fail-closed** (Fork P5-3). Pure data — stdlib only, no reflection. The guard tests
 * (`AiRequestGuardTest`, `OutboundSecretLeakGuardTest`) are hand-synced to these inventories,
 * mirroring the Phase-4 `PreferencesKeys.ALL_KEY_NAMES` / `RoomColumnNames.TABLE_NAMES` precedent.
 */
object OutboundContextPolicy {

    /**
     * Positive allow-list: the ONLY context categories permitted to leave the device. Adding an
     * entry is a privacy decision (Fork P5-3) — forbidden categories (device/usage/calendar/
     * location/history/contacts/clipboard) are deliberately **absent**.
     */
    enum class AllowedContext {
        USER_COMMAND,
        STATIC_SYSTEM_PROMPT,
        GENERATION_LIMITS,

        /**
         * The static Action Registry tool schema the AIL-4 router sends alongside the user command
         * (`CatalogSchemaRenderer`): action ids + human descriptions + argument names/descriptions.
         * Content-free and device-context-free by construction — it names *capabilities*, never any
         * calendar/location/usage/history/device value. Adding it is the one privacy widening AIL-4
         * makes, and it is guard-tested (the rendered schema is scanned for forbidden terms).
         */
        ACTION_CATALOG_SCHEMA,
    }

    val ALLOWED: Set<AllowedContext> = setOf(
        AllowedContext.USER_COMMAND,
        AllowedContext.STATIC_SYSTEM_PROMPT,
        AllowedContext.GENERATION_LIMITS,
        AllowedContext.ACTION_CATALOG_SCHEMA,
    )

    /**
     * Forbidden CONTEXT-source terms (Phase-4 family + context-leak family). Scanned over
     * **non-user-controlled text only** — the static system prompt and the field/category-name
     * inventories — **never over the user's typed command**, which is legitimate free text and may
     * contain any of these words (e.g. "open my calendar"). Scanning user content would
     * false-positive and corrupt legitimate commands.
     *
     * DELIBERATELY EXCLUDED (would false-match a legitimate identifier, so they are dropped to keep
     * the guard meaningful rather than vacuous — same reasoning as the Phase-4 `key`-excluded note):
     *  - `query` / `search` — a generation request legitimately concerns the user's query; the
     *    category is `USER_COMMAND`, and these would collide with ordinary wording.
     *  - `message` — collides with the legitimate [AiMessage] / `messages` outbound field.
     *  - `system` — is itself a legitimate field name and the `STATIC_SYSTEM_PROMPT` category name.
     */
    val FORBIDDEN_CONTEXT_TERMS: List<String> = listOf(
        "voice", "location", "calendar", "history", "conversation", "transcript",
        "usage", "contacts", "sms", "email", "clipboard",
    )

    /**
     * Credential terms — scanned over the field-name inventories ([OUTBOUND_FIELD_NAMES],
     * [AIERROR_FIELD_NAMES]) to prove **no outbound field can carry a key**. They WOULD fire if a
     * credential-named field (e.g. `apiKey` / `bearerToken` / `authorization`) were ever added.
     *
     * DELIBERATELY EXCLUDED — `token`:
     * the Phase-4 credential family includes `token`, but the legitimate outbound field
     * `maxOutputTokens` CONTAINS the substring `token` (lower-cased `maxoutputtokens`), so a
     * substring scan with `token` present would vacuously fail on a non-credential field. We drop
     * `token` and keep the meaningful credential family below — exactly the Phase-4 `key`-excluded
     * precedent (a term dropped because it collides with a legitimate identifier).
     *
     * NOTE on `credential`: it is retained here because it does not collide with any outbound
     * *field* name. It DOES appear inside the safe diagnostic *variant* name
     * [AiError.MissingCredentials] — which is exactly why these terms are scanned over the
     * field-name inventories, NOT over a rendered `AiError.toString()` (the leak guard scans the
     * rendered surface only for a planted secret sentinel). Same family of reasoning as the
     * confirmed `authorization` ⊄ `Unauthorized` non-collision.
     */
    val CREDENTIAL_TERMS: List<String> = listOf(
        "secret", "apikey", "api_key", "bearer", "authorization", "credential", "password",
    )

    /**
     * Hand-synced inventory of [AiRequest]'s outbound field names (Phase-4 `TABLE_NAMES` precedent).
     * RULE: adding a field to [AiRequest] requires updating this set — the guard enforces it stays
     * credential-free and allow-list-bounded.
     */
    val OUTBOUND_FIELD_NAMES: Set<String> =
        setOf("messages", "system", "maxOutputTokens", "model", "stopSequences")

    /**
     * Hand-synced inventory of [AiError]'s declared field names (all safe diagnostics). RULE: adding
     * a field to [AiError] requires updating this set — the leak guard enforces no credential-named
     * field is ever introduced on the outbound error surface.
     */
    val AIERROR_FIELD_NAMES: Set<String> =
        setOf("retryAfterMs", "detail", "statusCode")
}
