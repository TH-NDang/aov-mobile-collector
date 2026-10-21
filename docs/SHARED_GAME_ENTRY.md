# Shared game entry and quick settings

## v0.13.18

Every normal task start and resume now passes one service-level entry gate, including Pro, Chess, imported macros, and older macros without an `enter` step. It does not rewrite imported steps, saved indexes, capture ledgers, or calibrated coordinates. An existing first `enter` step is fulfilled once by the gate, not run twice.

The selected account is pinned to the run through the existing encrypted account store. UID verification is mandatory for normal collection, including legacy macros with `checkAccount: false` or no such property. If no account exists, account setup/selection is required. A changed or deleted pinned account blocks continuation. Teaching remains a separate dry run: it requires the configured UID to be readable and matching at the lobby, but does not fill credentials, look up UID through profile gestures, switch accounts, create captures, or alter the collection cursor. Missing or wrong UID stops teaching safely.

### Smart entry

- If the game is already foreground and the clear lobby is recognized, read its UID first. Matching UID skips force stop, relaunch, and the irrelevant startup popup wait.
- An unreadable UID uses the existing profile lookup during navigable entry, then fails safely if still unverified. A different UID uses the existing bounded sign-out/sign-in flow and verifies the result; it is never treated as success.
- If the game is not foreground, bring its existing activity forward, without force stopping it. Known loading/login/popup screens use the existing bounded safe entry path. Unknown screens time out with the screen preserved.
- Foreground package is checked before capture, after capture, and after OCR. Non-game pixels cannot masquerade as the lobby. A non-game Garena form can only be used by the existing explicitly initiated login flow.
- Cancellation, stop, timeout, and generation guards apply to the shared gate. Late screenshot/OCR callbacks cannot restart a cancelled task.

### Position-dependent tasks and resumes

Fresh list tasks navigate to the list; fresh ranking, warzone, chess, and Pro tasks navigate to the lobby. Current-hero tasks and arbitrary imported coordinate macros preserve the current screen. All mid-run resumes preserve the saved position.

For a position-dependent start/resume, the current screen must be recognized and its UID must match. UID OCR is attempted on all game pages, not only the lobby. No implicit trip to the lobby, profile, logout, or relaunch is allowed before replaying saved coordinates. If the UID is hidden or unreadable, the task pauses with an explanation. Open the correct screen with visible UID and retry, or start a new navigable task from the lobby. A stale previously verified UID or similar-looking screenshot is not identity proof and is not reused.

This conservative limitation is intentional: a game can change accounts without changing Android package, and screen positions cannot safely be reconstructed for an arbitrary interrupted coordinate macro.

### Floating settings

The compact draggable toolbar and expanded panel both have a labeled gear button. Quick settings use the same small accessibility window, with a bounded scroll area, non-focusable and non-touch-modal flags, and space reserved for system bars/cutouts. No fullscreen settings scrim is created.

- Smart lobby recognition: persisted under the shared UI preference `smartGameEntry`, default on, snapshotted when a run starts. Turning it off restores lobby settling, never force-stop behavior or an account-check bypass.
- Gesture visualization: reuses the existing persisted debug preference and can be changed live.
- Account management: links to the existing account screen, not a duplicate credential form.

Entry/account edits are disabled while a run is active, paused unfinished, teaching, or saving library data. Handlers recheck the same condition. Closing settings does not resume a paused task. No password is displayed or logged by this UI.

## Verification

Synthetic unit tests cover smart lobby, matching/missing/wrong UID, no force stop, current-screen preservation, cancellation during initial look, all built-in routing and imported macro routing, and every entry-settings lock combination. Existing GameEntry account-switch/profile-popup tests remain in place. No new screenshot input or user image is included.

Local JVM compilation uses Android API and external-library stubs, not the Android SDK resource/link pipeline. Android lint, APK assembly and actual Home/Recents touch behavior still require the repository's real GitHub Actions emulator job. Do not call the APK verified until that exact commit passes CI.
