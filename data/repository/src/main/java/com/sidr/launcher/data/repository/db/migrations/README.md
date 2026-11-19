# Room migrations runway

Package: `com.sidr.launcher.data.repository.db.migrations`

`SidrDatabase` shipped at **version 1** (Block F) with no `Migration` objects — this package was
the seeded runway (Fork 2). **Stage-2 S2-1 Task 9** bumped the database to **version 2** and added
`Migration1To2` (adds the `resolution_preferences` table); `Migration2To3` (S2-2) adds `aliases`;
`Migration3To4` (agentic A0 Task 10) adds the three `agent_*` tables; `Migration4To5` (A4′ phase 4,
Task 5) adds `learned_plans`. The database is at **version 5**. When an entity changes again:

1. Bump `SidrDatabase` `version`.
2. Add a `Migration(from, to)` here (prefer `@AutoMigration` where Room can derive it; a manual
   `Migration` object otherwise).
3. Commit the new golden schema JSON under `data/repository/schemas/`.
4. Cover the migration in the instrumented `MigrationTest` (`src/androidTest`) via
   `MigrationTestHelper` — and in the JVM gate: since schema 5 the schemas are unit-test assets too
   (`data/repository/build.gradle.kts`), so `MigrationTestHelper` runs under Robolectric
   (`Migration4To5Test`, `src/test`). The instrumented test is never run by an agent: it needs a device.
   One build-order trap, seen on the schema-5 run: the first build after a version bump merges the
   unit-test assets **before** KSP writes the new JSON, so that run's helper test cannot find it —
   the next run (with the JSON committed) can.

`fallbackToDestructiveMigration` is allowed **only in debug builds** and **only** for the
recreatable learning/cache tables (usage / suggestion-ranking / intent-match). Release builds
never destroy data.
