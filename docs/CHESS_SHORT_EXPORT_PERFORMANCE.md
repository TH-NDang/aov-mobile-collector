# Short native chess exports: readiness timing

Fast mode now checks a fresh frame immediately after a short icon's long press and after
pressing Sure. This applies only to faction, profession, equipment and the three blessing
image groups. The screenshot API still enforces its 1,050 ms interval and 250 ms overlay
settling delay.

An early pre-save frame can advance only when it contains the existing complete
Save Photo / Sure / Cancel confirmation. An early post-save frame can advance only when
the encyclopedia page is positively identified. Any ambiguous, partial, missing or still-open
result gets one fresh fallback read after the original 1,000 / 1,150 ms wait has elapsed.
There are at most two sequential reads per phase, never concurrent OCR or an unbounded poll.

The 850 ms long press, overlay leases, 15-second transaction timeout, durable save journal,
background import verification and 400 ms visible Stop/Pause interval are unchanged.
Hero/skill exports, slow mode, full detail scrolling, list-end checks and screenshot quality
are unchanged.

## Deterministic performance evidence

`NativeSaveReadinessTest.readyTwentyImageBatchUsesSameFortyReadsAndSavesThirtyFiveSeconds`
models 20 ready icons, including real gesture durations, screenshot interval and overlay
settling; it assumes 150 ms OCR and 40 ms journal work per image.

| 20 ready native exports | Fixed waits | Readiness-driven |
| --- | ---: | ---: |
| Simulated elapsed | 92,800 ms | 57,800 ms |
| Fresh screenshot/OCR reads | 40 | 40 |
| Extra fixed settle waits | 40 | 0 |
| Maximum concurrent reads | 1 | 1 |

This is a synthetic 35-second (37.7%) improvement, not a physical-phone benchmark.
Slow transitions or unreadable frames can cost an extra screenshot/OCR pass; the original
settling window remains the fallback and the existing transaction timeout remains the bound.

The focused suite also covers partial prompts, stale pre-save pages, post-Sure prompts,
missing frames, unknown screens, interrupted early/fallback reads, slow OCR and duplicate
callbacks. Run it with the project's Android SDK 35 / JDK 17 / Gradle 8.9 toolchain:

```sh
gradle --no-daemon testDebugUnitTest --tests vn.ndang.aovcollector.NativeSaveReadinessTest
```

Before release, the combined change still requires the normal unit tests, lint, APK build
and emulator smoke workflow. A real-game phone run is needed to measure actual elapsed time.
