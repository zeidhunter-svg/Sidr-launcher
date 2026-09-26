# Room migrations runway

Package: `com.sidr.launcher.data.repository.db.migrations`

`SidrDatabase` shipped at **version 1** (Block F) with no `Migration` objects — this package was
the seeded runway (Fork 2). **Stage-2 S2-1 Task 9** bumped the database to **version 2** and added
`Migration1To2` (adds the `resolution_preferences` table). When an entity changes again:

1. Bump `SidrDatabase` `version`.
2. Add a `Migration(from, to)` here (prefer `@AutoMigration` where Room can derive it; a manual
   `Migration` object otherwise).
3. Commit the new golden schema JSON under `data/repository/schemas/`.
4. Cover the migration in the instrumented `MigrationTest` (`src/androidTest`) via
   `MigrationTestHelper`.

`fallbackToDestructiveMigration` is allowed **only in debug builds** and **only** for the
recreatable learning/cache tables (usage / suggestion-ranking / intent-match). Release builds
never destroy data.
