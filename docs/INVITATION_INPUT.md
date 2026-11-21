# Invitation input window ownership (v0.13.28)

The reported screen contains a white native editor and an OK action above the keyboard, but the task stops before entering the code. The previous adapter inspected only `TYPE_INPUT_METHOD`: it could not find an editor owned by Unity's `TYPE_APPLICATION` dialog. The screenshot establishes the visible symptom and overlay overlap, not the runtime accessibility owner or whether the panel intercepted a touch.

## Selection and safeguards

- The selected IME must be present with its expected package, the game must remain present, and no foreign application may hold focus.
- Accept an unambiguous selected-IME editor, or a focused game-owned window containing a focused, non-password editable field and a separate aligned top-right OK action. Never choose a foreign window or guess coordinates for an unexposed editor.
- Read/write phases hide the collector panel briefly and allow the window update to settle. Stop remains available between phases rather than hiding controls for the entire polling deadline. The panel can briefly reappear between phases.
- A coordinate OK fallback is bounded to the verified editor-owner window, which may differ from the keyboard window. Its extra shield is released on completion, cancellation, rejected dispatch, or a bounded timeout. A timeout stops without retrying the action.
- Select all, clear, re-read empty, write once, and verify the exact code before OK. Require keyboard disappearance and a rendered game-code match before the existing runner may confirm. Existing account/submit ledger protections are unchanged.
- No screenshots or code values are logged or included in the repository. The fixture tests use synthetic node metadata.

## Verification

`EventInviteImePolicyTest` covers both editor owners, keyboard-only keys, rootless/ambiguous owners, foreign and unfocused windows, passwords, missing or misaligned OK actions, and the gate preventing submission while the keyboard remains visible. The normal required build, lint, JVM and Android UI/OCR suites remain enabled; no gate is weakened.

These tests do not establish this phone's actual accessibility hierarchy, OEM timing, or successful live game submission. If the device still cannot expose the editor, the task stops safely before entering a code. Phone verification remains required after installing the APK.
