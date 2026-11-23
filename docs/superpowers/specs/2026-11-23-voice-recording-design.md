# Voice Recording for Recipes: Design

Date: 2026-11-23 · Status: draft for review · Scope: v1

## Goal

Typing every field is hard for many users. Let a user save a recipe with **any combination** of typed fields, a photo, and one or more audio recordings. Nothing is mandatory except "at least one of them".

## Decisions (from brainstorming)

- Audio is stored as-is and played back. It is **not** transcribed in v1. This makes it language-agnostic: a user can speak in their native language.
- Pause / resume uses `MediaRecorder.pause()/resume()` (API 24, our minSdk). No stitching of `SpeechRecognizer` sessions.
- "Append" = more clips. Each stop produces one clip. A recipe holds a list of clips, and the user can add clips later when editing.
- Photo capture/pick already exists. Only its validation changes.
- Everything is optional. The only save rule is: at least one of text content, photo, or audio.

## Non-goals (v1)

- Speech-to-text and auto-filling fields from audio (phase 2, English-only parsing, `SpeechRecognizer` or on-device model).
- Cloud transcription or translation.
- Merging clips into one file, trimming, waveform UI.
- Fixing existing gaps unrelated to this feature (photo state lost on rotation, image files not deleted with a recipe).

## Data model

`RecipeEntity` / `Recipe`: add `audioPaths: List<String>` (default empty). Stored as JSON via the existing `Converters` (same as `tags`).

`prepTimeMinutes` and `servings` stay `Int` (non-null). **`0` means "not set".** SQLite cannot change a column's nullability in place, so we avoid a table rebuild.

Migration `MIGRATION_2_3`:

```sql
ALTER TABLE recipes ADD COLUMN audioPaths TEXT NOT NULL DEFAULT '[]'
```

`DatabaseModule` uses `fallbackToDestructiveMigration()`. If `MIGRATION_2_3` is not registered via `addMigrations(...)`, **every user's recipes are wiped on update**. Registering it is a release blocker, verified manually (see Testing).

## Components

| Unit | Responsibility | Depends on |
|---|---|---|
| `AudioRecorder` (`util/`) | Wraps `MediaRecorder` (MPEG_4 / AAC). `start()`, `pause()`, `resume()`, `stop(): File`, `release()`. Exposes `StateFlow<RecState>` = `Idle / Recording / Paused` and elapsed time. | `Context` |
| `AudioPlayer` (`util/`) | Wraps `MediaPlayer`. One clip plays at a time. `play(path)`, `pause()`, `stop()`, `release()`. Exposes the playing path. | none |
| `RecipeDraftRules` (`util/`) | Pure functions: `hasContent(...)` and `fallbackTitle(hasAudio, now)`. | none |
| `RecipeCreatorViewModel` | Holds the `AudioRecorder`, the list of clips (saved + new), and the pending-clip cleanup. Survives rotation. | `AudioRecorder` |
| `RecipeCreatorFragment` + layout | New "Voice notes" card: record / pause / resume / stop, and a row per clip (play, delete). Validation relaxed. | ViewModel, `AudioPlayer` |
| `RecipeViewerFragment` + layout | "Voice notes" section with the same clip rows, play only. | `AudioPlayer` |

## Behaviour

**Creator**
- Mic button → request `RECORD_AUDIO` on first use. If denied, show a snackbar and keep everything else usable.
- Record → `Recording` (timer shown). Pause → `Paused`. Resume → `Recording`. Stop → file finalised and appended to the clip list.
- Stop is also triggered automatically when the fragment stops (`onStop`) so a recording is never left dangling. Rotation (`isChangingConfigurations`) does not stop it; the recorder lives in the ViewModel.
- Save rule: `title/description/ingredients/instructions/tags` has non-blank text, **or** a photo exists, **or** at least one clip exists. Otherwise show "Add something first".
- Blank title → `fallbackTitle`: "Voice recipe · 23 Nov" if audio present, else "Untitled recipe · 23 Nov".
- Prep time / servings blank → saved as `0`. The required-field errors are removed.
- Edit mode: existing clips are shown. The user can add more clips or delete clips.

**Viewer / dashboard**
- Voice notes section shown only when `audioPaths` is non-empty.
- The stats row is hidden when both prep time and servings are `0`. If only one is set, the other shows "—" and the servings stepper is hidden when servings is `0`. Ingredient scaling already no-ops when base servings ≤ 0 (`UnitConverter.scaleQuantity`).
- Dashboard card (`RecipeAdapter`) uses the same rule.

**Files**
- Clips: `getExternalFilesDir(null)/recipe_audio_<uuid>.m4a` (same location convention as `ImageUtils`).
- New clips not saved (user leaves without saving, or deletes a clip before saving) are deleted.
- Clips of a deleted recipe are deleted in `RecipeRepositoryImpl.deleteRecipe`. Clips removed from a recipe during edit are deleted after a successful save.

## Error handling

- `MediaRecorder` throws on `prepare/start/stop` (mic busy, permission revoked, stop too quickly → `RuntimeException`). Catch, delete the partial file, return to `Idle`, show a snackbar. Never crash.
- Mic in use by another app (call) → same path.
- Missing audio file at playback (user cleared storage) → snackbar "Recording not found", row stays so it can be deleted.
- Low storage → surface the `IOException` from the recorder through the same snackbar path.

## Permissions

- `RECORD_AUDIO` in the manifest, requested at runtime via `ActivityResultContracts.RequestPermission`. Needed on first mic tap only. No other new permissions.

## Testing

- **Unit (JVM)**: `RecipeDraftRulesTest`: empty draft rejected; text-only, photo-only, audio-only accepted; blank title falls back with and without audio; whitespace-only text counts as empty.
- **Instrumented**: DAO round-trip for `audioPaths` (follow the existing `androidTest/.../data/local/dao` pattern); empty list and multiple paths.
- **Manual, required before release**: install the current (v2) build with sample recipes, upgrade to this build, confirm recipes survive and `audioPaths` defaults to `[]`. `exportSchema = false`, so `MigrationTestHelper` is not available.
- **Manual device pass**: record → pause → resume → stop → play; rotate during recording (state kept); background the app during recording (auto-stop, clip kept); deny mic permission; save with audio only; save with photo only; edit and add a second clip; delete a recipe and confirm files are gone.

## Phase 2 (not in this spec)

"Fill fields from recording": run speech-to-text over the clips, and for English locale parse title / servings / time / difficulty / instructions; for other languages, put the transcript into instructions. Separate spec when v1 ships.

## Risks

| Risk | Mitigation |
|---|---|
| Migration missing → data loss on update | Registered in `addMigrations`; manual upgrade test is a release gate |
| Large audio files fill storage | AAC at 64 kbps mono, which is ~0.5 MB/min. Cap a single clip at 10 min (auto-stop) |
| OEM `MediaRecorder` quirks on pause/resume | Pause/resume is API 24+. Manual test on at least one older and one current device |
