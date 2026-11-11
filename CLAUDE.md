# Health Tracker

Single-user Android health tracker backed by a private Supabase project. **The full spec is
[`docs/SPEC.md`](docs/SPEC.md); read it before starting any task** and update it when a decision
changes.

## Layout

```text
/android               Gradle project, single :app module (Kotlin, Compose, Hilt, Room+SQLCipher)
/supabase/migrations   SQL schema and RLS policies (one new file per change, never edit old ones)
/supabase/functions    Edge Functions (Deno/TypeScript); _shared/ holds config such as model names
/.github/workflows     android.yml (test, lint, signed APK -> Release "latest"), supabase.yml (migrate, deploy)
/docs                  SPEC.md, samples/
```

## Commands

Run from `android/` before every commit:

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

For changed Edge Functions: `deno test --allow-env --allow-read supabase/functions`.

Local builds read `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from environment variables or
`android/local.properties` (git-ignored). Without them the app builds but shows a "not configured"
message on the login screen.

## Android conventions

- Package `dev.healthtracker.app`. minSdk 28, compileSdk 37, targetSdk 36. Versions live in
  `android/gradle/libs.versions.toml`; AGP 9 has built-in Kotlin, so there is no `kotlin-android` plugin.
- Single activity (`MainActivity`, a `FragmentActivity` for BiometricPrompt), Navigation Compose with
  `@Serializable` routes, MVVM with `StateFlow`, Hilt for DI.
- Room database is encrypted with SQLCipher; the passphrase is wrapped by an Android Keystore key
  (`DatabaseKeyProvider`). Schema JSON is exported to `android/app/schemas/` — commit it.
  Add a Room migration for every schema change.
- Store metric units (kg, g, kcal, ms); convert for display with `core/units/UnitConverter`.
- Sync rows carry `id, source, source_record_id, updated_at, deleted_at`; merge with
  `core/sync/SyncMerge` (last write wins on `updated_at`). The server enforces the same rule with
  the `sync_before_update` trigger.
- Log only counts, timings and error types via `AppLogger` — never health values, photos, emails
  or keys. `LogSanitizer` is a backstop, not a licence.
- R8 is off for release builds until keep rules are verified on a device.

## Supabase conventions

- Every public table has RLS on with policies limited to `user_id = auth.uid()`. Tables written
  only by the server (`recovery_daily`, `coach_notes`, `polar_raw`) are read-only to clients.
  `polar_tokens` has no grants or policies, so only the service role can touch it.
- Meal photos live in the private `meal-photos` bucket under `<user id>/...`.
- Claude model names live only in `supabase/functions/_shared/config.ts`.
- Secrets (`ANTHROPIC_API_KEY`, `FDC_API_KEY`, `POLAR_CLIENT_ID`, `POLAR_CLIENT_SECRET`) are
  Supabase function secrets. Never put them in the app, the repo or GitHub.
- Supabase CLI 3.x: hosted-project commands need `--remote` and `--yes`/`SUPABASE_YES`.

## Rules

- Never commit keys, tokens, keystores or real health data. `.env.example` lists names only.
- The cloud VM can't run an emulator or reach the phone, so every PR lists what to check on the phone.
- Small branches, one phase step at a time.
