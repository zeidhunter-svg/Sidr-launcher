package com.sidr.launcher.domain.intent

import java.util.Locale

object CommandNormalizer {
    /**
     * Normalizes raw user input for matching: trim, collapse whitespace, lowercase with
     * Locale.ROOT (not Locale.getDefault() — device locale like Turkish changes "I"→"ı"
     * which breaks rule matching).
     *
     * The original raw string must be preserved by the caller for display and diagnostics.
     */
    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
