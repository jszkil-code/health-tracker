# Personal Health Tracker — Project Spec

Nov 11, 2026 · @scruffalicious

## Overview and goals

A single-user Android app that pulls in data from a Polar Loop Gen 2 and a Withings Body Smart scale, logs meals from my recipe app and from phone photos, and uses Claude to give daily guidance on training, rest, and meals. Data lives on the phone and in a private cloud I own. My doctor gets PDF and CSV exports.

Goals:

- One place for activity, workouts, sleep, HRV, heart rate, weight, body composition, and food intake.
- Meal logging in under 30 seconds: take a photo, optionally enter the weight, confirm.
- Macros come from a nutrition database, not from the model's guess.
- A daily coach that recommends training, rest, and meals and shows which numbers drove the advice.
- A doctor-ready report for any date range.

Non-goals: multiple users, a Play Store release, iOS, and anything that diagnoses or treats a medical condition.

## Architecture and tech stack

The phone reads and stores everything, Supabase keeps the synced copy, and only Supabase's server functions call outside services, so no key ever ships in the app.

&#91;embedded content: system architecture · phone, private cloud, outside services\]

Health Connect has no cloud API, so the phone always uploads device data itself. Results from the server functions land in the database and sync back down.

| Layer | Choice | Why |
| --- | --- | --- |
| App | Kotlin with Jetpack Compose | Health Connect, the camera, and background work are native-first on Android |
| Phone storage | Room with SQLCipher | Works offline and is encrypted at rest |
| Private cloud | Supabase: Postgres, Storage, Auth, Edge Functions | Database, photo storage, login, and server code in one place |
| AI | Claude API, called only from Edge Functions | Keeps the API key off the phone |
| Nutrition data | USDA FoodData Central | Free, authoritative macros per gram |
| Build and deploy | GitHub Actions | Builds the APK and deploys Supabase changes with no local machine |

## Data sources

Health Connect is the main pipe for device data; Polar's own API fills the gaps, and food comes from the Cookbook app and meal photos.

| Source | Path into the app | Data | Notes |
| --- | --- | --- | --- |
| Polar Loop Gen 2 | Polar Flow app → Health Connect → phone | Steps, heart rate, HRV, sleep, calories, workouts | Turn on Health Connect sync in Polar Flow ([details](https://www.fitmesh.fit/en/sync/polar)) |
| Polar Loop Gen 2 | [AccessLink API](https://www.polar.com/accesslink-api/) → server | Nightly Recharge, cardio load, detailed sleep, continuous HR | Polar-only metrics never reach Health Connect. OAuth 2.0, polling, transaction model, only 90 days of history from the day you connect ([details](https://openwearables.io/blog/polar-api-training-hrv-nightly-recharge-data)) |
| Withings Body Smart | Withings app → Health Connect → phone | Weight, body fat % | Confirm in Phase 1 which body composition fields arrive. Withings API is the fallback for any that don't |
| Cookbook PWA | Share Backup (.json) or text export → this app's share target or file picker | Recipes | The recipe format has no nutrition fields, so this app computes them on import |
| Meal photos | In-app camera + optional weight in grams | Foods, grams, macros | Claude identifies foods; USDA FoodData Central supplies the macros |

**Health Connect rules.** Request only the record types listed above. Include the permission-rationale screen Health Connect requires, plus background-read and history-read permissions so syncing works when the app is closed and older data can be backfilled.

**Polar timing.** Build the AccessLink connect step and a raw-JSON store early (end of Phase 1), even though the dashboard uses it later, because the 90-day history window starts when the account connects.

**Cookbook import format.** The Cookbook app stores recipes on the device and exports them two ways: Settings → Save Backup File or Share Backup (a .json file), and a text share where the JSON is base64-encoded between `---COOKBOOK-DATA-START---` and `---COOKBOOK-DATA-END---`. The importer accepts both, plus a bare array or an object that holds a recipe array, until a real backup confirms the top-level shape. Each recipe looks like this:

```json
{
  "title": "Recipe Name",
  "source": "https://...",
  "sourceLabel": "AllRecipes",
  "servings": 4,
  "prepTime": "15 min",
  "cookTime": "30 min",
  "category": "Dinner",
  "image": "",
  "ingredients": [{"amount": "1", "unit": "cup", "name": "flour"}],
  "instructions": ["First step."],
  "notes": ""
}
```

Category is one of Dinner, Lunch, Breakfast, Dessert, Snack, or Side. Amount and unit can be empty strings. Re-importing must not create duplicates: match on title plus source.

## Data model

The same tables exist on the phone (Room) and in Supabase (Postgres), and every row carries the fields needed to sync and de-duplicate.

| Table | Holds | Written by |
| --- | --- | --- |
| workouts | Type, start/end, duration, avg/max HR, calories, distance, cardio load | Phone (Health Connect), server (AccessLink) |
| sleep\_sessions | Start/end, stages, total sleep | Phone (Health Connect) |
| recovery\_daily | Nightly Recharge status, ANS charge, sleep charge | Server (AccessLink) |
| vitals\_daily | Resting HR, HRV (RMSSD), steps, active and total calories | Phone (Health Connect) |
| body\_measurements | Weight, body fat %, other body composition fields | Phone (Health Connect) |
| meals | Time, meal type, source (photo, recipe, manual), photo path, total weight, status (draft, confirmed), totals | Phone, server (analyze-meal) |
| meal\_items | Food name, USDA FDC id, grams, kcal, protein, carbs, fat, fiber, estimated flag | Phone, server (analyze-meal) |
| recipes | Cookbook fields, dedupe key, per-serving nutrition, import date | Phone (import), server (nutrition) |
| coach\_notes | Date, input summary, guidance JSON, model used | Server (coach) |
| goals | Training focus, nutrition targets, preferences | Phone (settings) |
| polar\_raw | Raw AccessLink JSON per transaction | Server |
| sync\_state | Last cursor and sync time per source | Phone, server |

Sync rules:

- Every row has a UUID `id`, `source`, `source_record_id`, `updated_at`, and `deleted_at` (soft delete). `(source, source_record_id)` is unique, so a re-sync never duplicates.
- The phone pushes rows it reads from Health Connect and rows the user creates. It pulls rows the server creates (Polar, AI results).
- Conflicts resolve by latest `updated_at`.
- Store metric units (kg, g, kcal, ms). Display US units (lb, oz) by default, switchable in settings.

## AI features

Claude does the recognizing and reasoning; USDA data and my own numbers supply the facts. Every Claude call runs in a Supabase Edge Function and returns JSON that matches a schema, so the app never parses free text.

| Job | Model | Trigger |
| --- | --- | --- |
| Meal photo analysis | Claude Sonnet 5.5 (`claude-sonnet-5-5`) | Each photo logged |
| Recipe ingredient normalization | Claude Haiku 5.5 (`claude-haiku-5-5`) | Each recipe imported |
| Daily coach | Claude Sonnet 5.5 | Each morning after overnight sync, and on demand |

Keep model names in one config value so they can change without code edits.

**Meal photo analysis (`analyze-meal`)**

1. The phone resizes the photo to about 1500 px on the long side, uploads it to private storage, and calls the function with the meal time and the optional total weight in grams.
2. Claude returns a list of items, each with a name, estimated grams, a USDA search phrase, and a confidence level, plus at most one clarifying question.
3. If a total weight was given, scale the item grams so they add up to it.
4. Look up each item in USDA FoodData Central, preferring Foundation and SR Legacy foods, and compute macros from grams.
5. If no USDA match is good enough, keep Claude's estimate and set `estimated = true` so the UI shows it.
6. Save the result as a draft meal. I can edit items and grams, then confirm.

**Recipe nutrition**

On import, Claude converts each ingredient line to grams and a USDA search phrase ("1 cup flour" becomes 125 g, "all-purpose flour"). The server looks up macros, totals them, divides by servings, and caches the result on the recipe. Logging a recipe meal means picking the recipe and entering servings eaten, such as 1.5. Ingredients with no amount, like "salt to taste," are skipped and listed on the recipe as not counted.

**Daily coach (`coach`)**

- Input: a compact JSON summary of the last 14 days covering sleep, HRV against my 30-day baseline, resting HR, Nightly Recharge, workouts and training load, the 7-day average weight trend, and food intake against my targets, plus my goals and preferences.
- Output: a readiness level (green, yellow, red), a training recommendation (type, duration, intensity), rest advice, nutrition guidance with protein and calorie targets and up to 3 meal ideas from my recipes, flags, and the list of metrics behind each recommendation.
- Guardrails in the system prompt: never diagnose, never recommend extreme calorie deficits, and flag unusual readings (a resting HR jump, fast weight change, several nights of poor sleep) with a suggestion to check with my doctor.
- Cache the static system prompt with prompt caching to cut cost.
- A later add-on is an "Ask the coach" chat that uses the same context.

## Doctor exports

The phone builds a PDF and a CSV for any date range and hands them to the Android share sheet, so I can attach them to a MyChart message, email them, or print them. Nothing is sent automatically.

The PDF holds:

1. A header with my name, the date range, and the date generated.
2. A summary table of start, end, and change for weight and body fat %, plus averages for resting HR, HRV, sleep duration, daily steps, weekly active minutes, workout count, and daily calories, protein, carbs, and fat.
3. Trend charts for weight, resting HR, HRV, and sleep.
4. Any coach flags raised in the range.
5. A short methods note saying device data comes from Polar and Withings and meal macros are estimates from photos, recipes, and USDA data.

The CSV has one row per day with every daily metric, for anyone who wants the raw numbers. Build both on the phone so exports work offline.

## Security and privacy

Secrets live only on the server, and health data is encrypted on the phone and locked to my account in the cloud.

- **No secrets in the app or repo.** The Anthropic key, Polar client secret, and USDA key are Supabase function secrets. The app holds only the Supabase URL, its publishable key, and my login session. The repo has a `.env.example` with names only.
- **Database rules.** Row-level security is on for every table, and each policy limits rows to `user_id = auth.uid()`. Turn off new sign-ups once my account exists.
- **Photos.** Meal photos go in a private storage bucket and load through short-lived signed URLs.
- **Polar tokens.** Store them server-side only, in a table no client role can read.
- **Phone storage.** Encrypt the Room database with SQLCipher, with the key in Android Keystore. Require a fingerprint or PIN to open the app. Exclude the database from Android auto-backup, since the private cloud is the backup.
- **Logs.** A debug log screen records sync and API events (counts, timings, errors) but never raw health values, photos, or keys.
- **Exports.** Generated only when I ask, and shared only through the share sheet.

## Build phases

Build in seven phases, each ending with an installable APK I can test on my phone before the next one starts.

1. **Foundation.** Android project skeleton, Supabase project with the schema migration and RLS, login, GitHub Actions that build a signed APK and deploy Supabase changes, and the debug log screen.
   - Done when: I install the APK from GitHub on my phone, log in, and see an empty dashboard.
2. **Health Connect sync and dashboard.** Permissions, reads for every record type in Data sources, background sync every few hours plus a manual Sync button, push to Supabase, and a dashboard with today, 7-day, and 30-day views. At the end, add the Polar connect step and raw-JSON store.
   - Done when: last night's sleep and this morning's weigh-in appear after a sync, and Polar shows as connected.
3. **Meal photo logging.** Camera screen, optional weight entry, `analyze-meal`, an edit-and-confirm screen, manual entry, and daily macro totals.
   - Done when: a weighed meal is logged in under 30 seconds, and a packaged food's macros land within about 10% of its label.
4. **Recipe import.** Share target and file picker for both Cookbook formats, per-serving nutrition, and logging by servings.
   - Done when: a full backup imports, re-importing adds no duplicates, and 1.5 servings logs correctly.
5. **AI coach.** Goals screen, morning job, coach card with readiness, training, rest, and nutrition, and the metrics behind each.
   - Done when: a card is waiting each morning and each recommendation names the numbers that drove it.
6. **Doctor export.** Date-range picker, PDF, CSV, and share sheet.
   - Done when: a 90-day PDF opens cleanly and attaches to a MyChart message.
7. **Polar extras.** Scheduled AccessLink pulls, Nightly Recharge and cardio load on the dashboard and in the coach input.
   - Done when: Nightly Recharge shows each morning and changes the coach's readiness call.

## Accounts, keys, and configuration

Each key has exactly one home, and none of them are in the repo.

| Item | Where to get it | Where it's stored | Needed by |
| --- | --- | --- | --- |
| Private GitHub repo | github.com | n/a | Phase 1 |
| Supabase project URL and publishable key | supabase.com, project settings | GitHub Actions secrets, injected into the APK at build time | Phase 1 |
| Supabase access token | Supabase account settings | GitHub Actions secret `SUPABASE_ACCESS_TOKEN` | Phase 1 |
| Supabase project ref and database password | supabase.com, project settings | GitHub Actions secrets `SUPABASE_PROJECT_REF`, `SUPABASE_DB_PASSWORD` (used by `supabase db push`) | Phase 1 |
| Android signing keystore | Generated once with `keytool` | GitHub Actions secrets (base64 keystore plus passwords) | Phase 1 |
| Anthropic API key | Claude Console (platform.claude.com) | Supabase secret `ANTHROPIC_API_KEY` | Phase 3 |
| USDA FoodData Central key | api.data.gov sign-up (free) | Supabase secret `FDC_API_KEY` | Phase 3 |
| Polar AccessLink client ID and secret | [admin.polaraccesslink.com](https://admin.polaraccesslink.com) | Supabase secrets `POLAR_CLIENT_ID`, `POLAR_CLIENT_SECRET` | Phase 2 |

The keystore must stay the same for every build, or Android refuses to install updates over the existing app.

On the phone, turn on Health Connect sync in the Polar Flow app and in the Withings app before Phase 2.

## Working rules for Claude Code

Claude Code works in a cloud VM from the GitHub repo, so this spec lives in the repo as `docs/SPEC.md` and `CLAUDE.md` points to it.

**Repo layout**

```text
/android               Gradle project, single app module
/supabase/migrations   SQL schema and RLS policies
/supabase/functions    analyze-meal, recipe-nutrition, coach, polar-auth, polar-sync
/.github/workflows     android.yml, supabase.yml
/docs                  SPEC.md, samples/ (a scrubbed Cookbook backup)
CLAUDE.md
```

**Android conventions**

- Kotlin, Jetpack Compose with Material 3, single activity, Navigation Compose.
- MVVM with ViewModel and StateFlow, Hilt, Room with SQLCipher, WorkManager, CameraX, the supabase-kt client, and kotlinx.serialization.
- Current stable library versions, kept in a Gradle version catalog.
- minSdk 28, the lowest Android version Health Connect supports, unless my phone answer changes it.
- compileSdk 37 (current AndroidX releases require it), targetSdk 36 until API 37 behavior changes are tested on the phone.

**Server conventions**

- Edge Functions in TypeScript (Deno), one folder each, sharing the JSON schemas the app uses.
- Every schema change is a new migration file. Never edit an old one.

**Testing and workflow**

- Unit tests cover the Cookbook import parsers, unit conversion, USDA mapping, gram scaling, and sync de-duplication.
- Before every commit, run `./gradlew testDebugUnitTest lint assembleDebug` and `deno test` for changed functions.
- The cloud VM can't run an emulator or reach my phone, so each pull request lists what I should check on the phone.
- Work in small branches, one phase step at a time. Update `docs/SPEC.md` when a decision changes.
- Never commit keys, tokens, or real health data. Sample files must be scrubbed.

**CI**

- `android.yml`: on each push to main, build a release APK signed with the stored keystore and publish it to a GitHub Release tagged `latest`, so I can download and install it on my phone.
- `supabase.yml`: on changes under `/supabase`, apply migrations and deploy functions.

**Cloud environment for Claude Code on the web**

The VM comes with OpenJDK 21 and Gradle but not the Android SDK. Google's Maven and Gradle are on the default Trusted allowlist, but the SDK downloads come from dl.google.com, which isn't ([allowlist](https://code.claude.com/docs/en/cloud-environments.md)). Create an environment named `android` with these settings:

- Network access: Custom, allowed domain `dl.google.com`, with "Also include default list of common package managers" checked.
- Environment variables: `ANDROID_HOME=/opt/android-sdk`
- Setup script:

```bash
#!/bin/bash
set -e
export ANDROID_HOME=/opt/android-sdk
apt-get update -y && apt-get install -y unzip || true
mkdir -p $ANDROID_HOME/cmdline-tools
cd /tmp
curl -sSL -o tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null || true
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0"
```

The setup script must finish in about 5 minutes to be cached. Supabase deploys run in GitHub Actions, not in the Claude Code session.

## Open questions

- [ ] Which phone and Android version will run the app? This sets minSdk and whether Health Connect is built in.
- [ ] What are my goals for the coach (fat loss, strength, endurance, maintenance), and do I set calorie and protein targets or does the coach propose them?
- [ ] Which other devices and apps should feed in? Peloton rides could be recorded on the Loop or pulled from Peloton.
- [ ] Is the Cookbook backup file a bare array or an object with a recipe list? Add a scrubbed sample to `docs/samples/`.
- [ ] Which Withings body composition fields reach Health Connect beyond weight and body fat %?
- [ ] Which metrics does my doctor want, and in what format?
- [ ] How long should meal photos be kept after a meal is confirmed?
