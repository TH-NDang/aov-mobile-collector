# Floating panel controls (v0.13.30)

- The collapsed panel contains only the AOV handle. Tap it to expand; drag the handle to reposition.
- The expanded header contains accessible 48 dp gear, pencil, image-history, and collapse actions. Gear opens the existing quick settings; pencil uses the existing guarded step editor. Run/Pause and Stop remain separate labeled actions below.
- Drag past Android's touch slop to reveal a bottom trash target above the system navigation area. Release inside the highlighted target to **pause and close the panel**, matching the existing Close action. This never deletes captures, resets the run, or changes its saved progress. Release outside to reposition; cancel hides the target without closing. The existing Close button remains available without dragging.
- The trash target is visual only (`FLAG_NOT_TOUCHABLE`, `FLAG_NOT_FOCUSABLE`); it cannot intercept navigation or game touches. Shielding, rebuilding, rotation, closing, and service teardown remove it.
- Image progress uses a high-contrast 48 dp vector close button with touch feedback. Closing that window does not delete images.
- Image progress retains its last complete status during background refresh. The old immediate short status assignment removed “Ảnh gốc” every refresh before adding it back, repeatedly resizing the rows. Complete snapshots now update only when changed; a fixed five-line status area prevents row jumps. Changing runs clears old counters, and late snapshots cannot restore them.

## Verification

`CaptureProgressStatusTest` covers repeated loading/completion, unchanged snapshots, run changes, and late callbacks after close. `QuickSettingsSmoke` checks header target size/non-overlap, compact/expanded resizing, gear behavior, the pencil busy guard, image-progress close/status layout, real Android navigation, and six drag paths (outside/cancel/trash in both compact and expanded modes). Existing history smoke opens its icon by accessible description.

Android smoke runs require the repository's offline fixture/emulator workflow. These checks do not claim successful operation inside the live game or on the user's phone.

## Compact sizing and raised drop target (v0.13.31)

The collapsed window is now approximately 56 × 56 dp **including** its padding, with a 48 × 48 dp tap/drag handle. Its pixel size is composed from the converted handle plus both paddings, avoiding fractional-density rounding that could shrink the handle or make the window nonsquare. It no longer retains the old 152 dp expanded pill width. Expanding restores the full controls; repeated collapse and configuration changes retain the compact dimensions.

The 64 dp trash target is positioned with its bottom edge 80 dp above the safe navigation edge, rather than 12 dp. Its position and size are clamped inside system-bar/cutout insets on short landscape displays. The pause-and-close semantics, non-touchable target, cancellation, shielding, and no-deletion guarantees are unchanged.

`PanelOverlayGeometryTest` exercises the production placement function for portrait, landscape, cutouts, density scaling, and tiny safe areas. The overlay smoke asserts actual 56 dp window / 48 dp handle dimensions, raised target bounds, repeated compact drags, and target cleanup during the configuration-change callback in addition to outside/cancel/drop paths. This callback test is distinct from a physical-device rotation test.
