# Google Play Release Plan

## Goal

Publish AI Index Finger through Google Play internal testing and then production without weakening its user-controlled, on-device automation model. All workflow data and run history remain local unless the product and privacy disclosures are deliberately changed.

## Current Baseline

- Kotlin 2.1, Java 17, min SDK 26, target and compile SDK 36.
- Core workflow editing, execution, persistence, import/export, scheduling, node inspection, and run history are implemented.
- Existing JVM and Android unit tests pass.
- The application has no network permission.
- The unsigned release AAB builds successfully at `app/build/outputs/bundle/release/app-release.aab`.
- Release lint passes with eight non-blocking recommendations: four dependency updates plus `ObsoleteSdkInt`, `AutoboxingStateCreation`, `UnusedResources`, and `UseKtx`. Backup and launcher-icon warnings are resolved.
- Release signing, store assets, privacy-policy hosting, Play Console declarations, and physical-device checks require external owner input.

## P0 - Required Before Internal Testing

| Status | Item | Acceptance gate |
| --- | --- | --- |
| Device check pending | Prominent Accessibility API disclosure and affirmative consent | Code and focused unit tests are complete. On a clean install, verify every in-app path shows disclosure before first acknowledgement, decline stays in-app, acceptance opens Settings, and disclosure remains reviewable. |
| Device check pending | Minimize accessibility observation lifetime | Snapshot collection now requires an editor or inspector lease, the final release clears retained nodes, and service destruction clears data. Verify target-app round trips and service disable/re-enable on a device. |
| Device check pending | Visual element capture | On Android 11+, Click editing can capture the previous app once and select an accessibility element by tapping the in-memory image. Verify repeated controls, rotation, protected-window failure, timeout, service disablement, and bitmap disposal; update the Accessibility API declaration and privacy policy before upload. |
| Planned | Upload-key release signing | `bundleRelease` produces an upload-key-signed AAB using credentials outside Git; Play App Signing is enabled. |
| External | Privacy policy and Data Safety form | Public policy URL and Play declarations accurately describe screen data, package names, clipboard use, local files, exports, and retention. |
| External | Accessibility API declaration | Core purpose, user benefit, activation, collected data, and actions match actual behavior; demonstration video is prepared if requested. |

## P1 - Required Before Production

| Status | Item | Acceptance gate |
| --- | --- | --- |
| Brand check pending | Launcher icon and store identity | Legacy, adaptive, round, and Android themed icon resources compile and are referenced by the release manifest. Confirm the visual identity on physical launchers and approve the permanent app name and application ID. |
| Complete | Explicit backup and transfer exclusions | Android 12+ cloud backup and device transfer plus legacy full backup exclude all internal files, preferences, databases, and external app data; the merged release manifest references both rule sets. |
| Complete | Save-time workflow validity | Incomplete workflows remain explicit drafts; only validator-clean ready workflows can run or be scheduled. Legacy workflows derive their effective state without a format break. |
| Planned | Accessibility and lifecycle device tests | Core flow passes with TalkBack, permission denial/recovery, process restart, and service disablement. |
| In progress | Release lint and bundle gate | `test`, `lintRelease`, and `bundleRelease` pass. Resolve or explicitly document the eight remaining lint warnings before production. |
| External | Store listing package | Phone screenshots, feature graphic, descriptions, support email, content rating, target audience, and app-access answers are complete. |

## P2 - Post-Launch Quality Backlog

- Guided first-run experience and safe example workflows.
- Richer execution failure context and exportable run diagnostics.
- Recurring daily and weekly reminders are implemented; battery, timezone, DST, reboot, and notification-recovery behavior still require device testing.
- Broader UI and accessibility instrumentation coverage.
- Localization based on launch-market demand.

## Completed UX Recovery Improvements

- Click, Long Click, and Input Text share the one-shot visual element capture flow.
- Preflight findings offer direct accessibility and notification recovery actions.
- Validation and selector findings can open the exact top-level or nested step editor.
- Failed run details can reopen the current workflow at the failed step, with safe stale-step fallback.
- Editor validation issues are expandable and actionable instead of silently hiding later blockers.
- Fired schedule reminders reconcile in-memory state, queue rapid intents, and use collision-resistant notification identities.

## Functional Improvement Order

| Status | Priority | Feature | Acceptance gate |
| --- | --- | --- | --- |
| Device check pending | 1 | Preserve clipboard around Paste input | Original clipboard content is restored after success or failure only while the operation still owns the temporary clip; newer clipboard changes are never overwritten. |
| Complete | 2 | Fully edit nested Repeat and If branches | Stable path-based editing supports add, edit, duplicate, reorder, and delete in Repeat contents and both If branches without changing unrelated IDs. |
| Complete | 3 | Draft and ready workflow states | Incomplete workflows remain saveable as drafts but cannot run or be scheduled; ready workflows pass `WorkflowValidator`. |
| Complete | 4 | Run-history details and export | Users can inspect all retained runs, filter them, reopen workflows, and export local diagnostics. |
| Complete | 5 | Safe starter examples | A searchable bilingual catalog contains 100 reviewed examples across ten categories. Every selection creates a fresh explicit draft and never runs automatically; app-observation and text-reading examples use visible placeholders and require user configuration before use. |
| Complete | 6 | User workflow folders | Users can create, rename, delete, filter, and move workflows among single-level folders or Unfiled. Folder deletion preserves workflows, and versioned backups round-trip empty folders and assignments while legacy data remains importable. The complete Compose interaction flow passes on the API 36 emulator. |
| Complete | 7 | Tested system-app workflow packs | An explicit bilingual action idempotently installs nine real Draft workflows across Settings, Clock, and Files folders without overwriting user edits. All nine verify real package-scoped UI through the production accessibility service on the API 36 emulator and persist Completed run records. Calculator is not installed on the test image, and the remaining catalog entries are not claimed as device-tested workflows. |
| Complete | 8 | Preflight checks | User-triggered snapshots report validation, Draft/Ready state, variable dataflow, structural and retry-expanded execution limits, launchability, selector match indexes, service state, and notification permission without dispatching actions or writing history. |
| Device check pending | 9 | Date/time scheduling | Users choose an absolute local date and time. Past, DST-gap, and over-one-year targets are rejected; overlap behavior is deterministic; WorkManager remains a best-effort one-time reminder and never executes automation. Verify delivery and timezone-change behavior on devices. |

## Release Validation

1. Run `gradlew.bat test`.
2. Run `gradlew.bat lintRelease`.
3. Run `gradlew.bat bundleRelease` with external upload-key configuration.
4. Install on at least one API 26 device and one device near target SDK 36.
5. Exercise create, configure, save, reopen, validate, disclose, enable, run, stop, review history, export, import, schedule, deny permissions, and recover permissions.
6. Upload to Play internal testing and resolve pre-launch report crashes, policy warnings, and accessibility findings before production rollout.

## Next Iteration

Verify preflight and one-time/daily/weekly reminder flows on API 26 and a target-SDK 36 device, including notification denial/recovery, missed occurrences, DST-adjacent inputs, timezone changes, process restart, replacement, and cancellation. Then prioritize completing workflow-editor localization and broader accessibility instrumentation.

## Owner Decisions

- Confirm that user-authored cross-app automation is the product's Google Play core purpose and allowed use case.
- Confirm the permanent application ID `com.aiindexfinger` and signing-key owner.
- Supply the privacy-policy URL, support email, launch regions/languages, listing assets, and Play Console access.