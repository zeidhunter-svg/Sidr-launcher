package com.sidr.launcher.data.repository.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration runway test (Block F, step F8.5 / Fork 2).
 *
 * Validates that the v1 golden schema (schemas/1.json) matches what Room actually creates.
 * This is the baseline for all future migrations: any entity change must bump the version,
 * add a Migration object, and pass this test.
 *
 * Run with: ./gradlew :data:repository:connectedDebugAndroidTest
 * (Requires a connected device or emulator — not run in CI JVM pipeline.)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SidrDatabase::class.java,
    )

    @Test
    fun v1_goldenSchema_createsAndOpensWithoutError() {
        // createDatabase validates 1.json identity hash against the live schema.
        // If the entity definition drifts from the committed JSON, this throws.
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun v1_migrationValidation_passesAsRunway() {
        helper.createDatabase(TEST_DB, 1).close()
        // Validate schema after running no migrations (empty list) — confirms the schema
        // validator is wired and ready for future Migration objects.
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    private companion object {
        const val TEST_DB = "sidr-migration-test"
    }
}
