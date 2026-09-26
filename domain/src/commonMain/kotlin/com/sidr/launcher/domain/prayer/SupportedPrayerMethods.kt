package com.sidr.launcher.domain.prayer

/**
 * One calculation method offered at first-run setup (DS-6B Task 8, spec §0.2). [id] is the opaque
 * [CalculationMethodId] key persisted in [PrayerSetup]; [displayLabel] is presentation copy only.
 */
data class SupportedPrayerMethod(
    val id: CalculationMethodId,
    val displayLabel: String,
)

/**
 * The full standard method catalog (DS-6B Task 8). This is the ONLY place `:domain`/`:feature`
 * enumerate calculation methods — the UI must never invent a key. Every entry's [SupportedPrayerMethod.id]
 * key MUST have a mapping in `:data:prayer`'s `AdhanPrayerCalculator` method table, so the UI can never
 * offer a method the calculation adapter rejects; this is enforced by an anti-drift test
 * (`AdhanPrayerCalculatorSupportedMethodsTest`) that asserts a real calculation succeeds for every
 * entry here.
 *
 * [SupportedPrayerMethod.id] keys mirror adhan2's own `CalculationMethod` enum constant names 1:1
 * (documented, not accidental) — this keeps the domain key and the adapter's lookup table trivially
 * auditable against each other. `OTHER` is deliberately excluded: it is adhan2's generic/uncalibrated
 * fallback bucket, never a real named authority a user would knowingly choose.
 *
 * `TEHRAN` is NOT offered: `com.batoulapps.adhan:adhan2:0.0.5` (the pinned version, see
 * `AdhanPrayerCalculatorGoldenTest`'s header for why this exact version is pinned) has no such
 * `CalculationMethod` constant — verified directly against the resolved jar via `javap`
 * (`com.batoulapps.adhan2.CalculationMethod`), which lists exactly: `MUSLIM_WORLD_LEAGUE`,
 * `EGYPTIAN`, `KARACHI`, `UMM_AL_QURA`, `DUBAI`, `MOON_SIGHTING_COMMITTEE`, `NORTH_AMERICA`,
 * `KUWAIT`, `QATAR`, `SINGAPORE`, `TURKEY`, `OTHER`. A method this library does not implement is
 * never offered as a first-run choice a user could pick and silently get the wrong (`OTHER`/default)
 * answer for.
 *
 * Order is display order.
 */
object SupportedPrayerMethods {
    val ALL: List<SupportedPrayerMethod> = listOf(
        SupportedPrayerMethod(CalculationMethodId("MWL"), "Muslim World League"),
        SupportedPrayerMethod(CalculationMethodId("EGYPTIAN"), "Egyptian General Authority of Survey"),
        SupportedPrayerMethod(CalculationMethodId("KARACHI"), "University of Islamic Sciences, Karachi"),
        SupportedPrayerMethod(CalculationMethodId("UMM_AL_QURA"), "Umm al-Qura University, Makkah"),
        SupportedPrayerMethod(CalculationMethodId("DUBAI"), "Dubai (UAE)"),
        SupportedPrayerMethod(
            CalculationMethodId("MOON_SIGHTING_COMMITTEE"),
            "Moonsighting Committee Worldwide",
        ),
        SupportedPrayerMethod(
            CalculationMethodId("NORTH_AMERICA"),
            "Islamic Society of North America (ISNA)",
        ),
        SupportedPrayerMethod(CalculationMethodId("KUWAIT"), "Kuwait"),
        SupportedPrayerMethod(CalculationMethodId("QATAR"), "Qatar"),
        SupportedPrayerMethod(CalculationMethodId("SINGAPORE"), "Majlis Ugama Islam Singapura"),
        SupportedPrayerMethod(CalculationMethodId("TURKEY"), "Diyanet İşleri Başkanlığı, Turkey"),
    )
}
