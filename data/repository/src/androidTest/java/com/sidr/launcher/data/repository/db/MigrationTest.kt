package com.sidr.launcher.data.repository.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.data.repository.db.migrations.Migration1To2
import com.sidr.launcher.data.repository.db.migrations.Migration2To3
import com.sidr.launcher.data.repository.db.migrations.Migration3To4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration runway test (Block F, step F8.5 / Fork 2; v1->v2 case added Stage-2
 * S2-1 Task 9).
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

    @Test
    fun v1_to_v2_migration_addsResolutionPreferencesTable_andValidatesAgainstGoldenSchema() {
        // Create the DB at v1 (schemas/1.json — the frozen 3-entity historical shape).
        helper.createDatabase(TEST_DB, 1).close()

        // Run Migration1To2 and validate the resulting schema against schemas/2.json (4 entities,
        // incl. resolution_preferences). Throws if Migration1To2's CREATE TABLE SQL drifts from
        // the golden schema (column order, NOT NULL, or the composite primary key).
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).close()
    }

    @Test
    fun v2_to_v3_migration_addsAliasesTable_andValidatesAgainstGoldenSchema() {
        // Create the DB at v2 (schemas/2.json — history tables + resolution_preferences).
        helper.createDatabase(TEST_DB, 2).close()

        // Run Migration2To3 and validate the resulting schema against schemas/3.json (adds aliases).
        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration2To3).close()
    }

    @Test
    fun v3_to_v4_migration_addsAgentSessionTables_andValidatesAgainstGoldenSchema() {
        // Create the DB at v3 (schemas/3.json — history tables + resolution_preferences + aliases).
        helper.createDatabase(TEST_DB, 3).close()

        // Run Migration3To4 and validate the resulting schema against schemas/4.json (adds
        // agent_session, agent_plan_step, agent_trace_event). Throws if Migration3To4's CREATE TABLE
        // SQL drifts from the golden schema — a re-typed NOT NULL, a dropped composite primary key,
        // or a missing ON DELETE CASCADE, which is the clause the at-rest-empty guarantee rests on.
        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration3To4).close()
    }

    /**
     * The whole runway in one go: a database created at v1 must reach v4 through the three migration
     * objects in sequence. The per-step cases above each validate one hop against its golden schema;
     * this one is the only case that proves they compose, which is what a device upgrading from an
     * old install actually does.
     */
    @Test
    fun v1_to_v4_migrationRunway_composes() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration1To2, Migration2To3, Migration3To4).close()
    }

    private companion object {
        const val TEST_DB = "sidr-migration-test"
    }
}
