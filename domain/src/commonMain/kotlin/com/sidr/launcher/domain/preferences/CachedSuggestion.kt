package com.sidr.launcher.domain.preferences

/**
 * Minimal display projection stored in the suggestions cache.
 *
 * This is a UI-repaint cache, not a behavioural-history store. Only fields needed to
 * repaint the last suggestion surface on cold start are kept here. The serialisation DTO
 * (CachedSuggestionDto) lives in :data:repository and carries @Serializable; this domain
 * model is annotation-free.
 *
 * Field-by-field split (mirrors PreferencesKeys anchor comment):
 *   CACHED   label    — display text
 *   CACHED   actionId — offline-resolvable package name or route constant
 *   NOT CACHED  raw query text the user typed
 *   NOT CACHED  search or match timestamps
 *   NOT CACHED  location- or calendar-derived context
 *   NOT CACHED  confidence score or match provenance
 */
data class CachedSuggestion(
    val label: String,
    val actionId: String,
)
