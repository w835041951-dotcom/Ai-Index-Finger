# Google Play Release Plan

## Goal

Publish AI Index Finger through Google Play internal testing and then production without weakening its user-controlled, on-device automation model. All workflow data and run history remain local unless the product and privacy disclosures are deliberately changed.

## Current Baseline

- Current prerelease `0.33.0-beta.12` (`versionCode 43`), Kotlin 2.1, Java 17, min SDK 26, target and compile SDK 36.
- Core workflow editing, execution, persistence, import/export, scheduling, node inspection, and run history are implemented.
- Core and app JVM tests pass. Structured validator and executor errors are localized through English and Simplified Chinese resources, and new run-history records persist stable error codes while legacy text records remain readable.
- The application does not request Android's `INTERNET` permission and does not transmit workflow data; WorkManager contributes normal scheduling permissions such as `ACCESS_NETWORK_STATE`.
- Unsigned release APK and AAB builds succeed. These artifacts are build evidence only and cannot be uploaded as release candidates.
- Release lint passes with 0 errors and 245 warnings: 211 `UnusedResources`, 20 `PluralsCandidate`, 7 dependency notices, 4 `UseKtx`, and one each of `AutoboxingStateCreation`, `DiscouragedApi`, `ObsoleteSdkInt`, and `UnusedAttribute`.
- The repository has no GitHub Actions workflow and no release-signing configuration. Release signing, store assets, privacy-policy hosting, Play Console declarations, and physical-device checks require external owner input.

## Production Release Gates

| Gate | Owner | Current state | Required evidence | Production blocker |
| --- | --- | --- | --- | --- |
| Release identity | Product owner | `com.aiindexfinger`, `0.33.0-beta.12`, code 43 | Confirm permanent application ID, public version name, and incremented version code for the first uploaded candidate | Yes |
| Upload signing | Release owner | Not configured | Upload keystore and alias stored outside Git; signed AAB verified with `jarsigner` or `apksigner`; Play App Signing enabled | Yes |
| Automated build gate | Engineering | Local commands pass; no CI | Protected GitHub workflow runs tests, release lint, and unsigned bundle build for the release commit | Yes |
| Bilingual product UI | Engineering/QA | Resource keys match; remaining hardcoded Compose text exists outside the newly localized run-history flow | English and Simplified Chinese smoke-test checklist passes with no mixed-language critical workflow | Yes |
| Accessibility disclosure | Product/QA | Code and focused tests complete | Clean-install device recording proves disclosure, decline, acceptance, Settings handoff, and reviewability | Yes |
| Accessibility automation lifecycle | QA | Unit coverage present | Device matrix proves observation leases, service disable/re-enable, process restart, stop, and protected-screen behavior | Yes |
| Scheduling and notifications | QA | Unit coverage present | API 26 and API 36 evidence for denial/recovery, recurrence, missed reminders, reboot, timezone/DST, replacement, and cancellation | Yes |
| Privacy policy and Data Safety | Product/legal | Not supplied | Public URL and approved declarations covering accessibility data, screenshots, package names, clipboard, local storage, exports, and retention | Yes |
| Accessibility API declaration | Product/legal | Not supplied | Play Console declaration and demonstration video match actual behavior and user-controlled core purpose | Yes |
| Store listing | Product/design | Not supplied | Approved icon, screenshots, feature graphic, descriptions, support email, target audience, content rating, regions, and app-access answers | Yes |
| Pre-launch report | Release owner/QA | Not run | No unresolved crash, ANR, policy, accessibility, or compatibility blocker on uploaded signed candidate | Yes |
| Lint debt | Engineering | 0 errors, 245 warnings | Triage all warnings; remove accidental unused resources and document accepted warnings. No new release-critical warning | No, unless triage finds a defect |

## P0 - Required Before Internal Testing

| Status | Item | Acceptance gate |
| --- | --- | --- |
| Device check pending | Prominent Accessibility API disclosure and affirmative consent | Code and focused unit tests are complete. On a clean install, verify every in-app path shows disclosure before first acknowledgement, decline stays in-app, acceptance opens Settings, and disclosure remains reviewable. |
| Device check pending | Minimize accessibility observation lifetime | Snapshot collection now requires an editor or inspector lease, the final release clears retained nodes, and service destruction clears data. Verify target-app round trips and service disable/re-enable on a device. |
| Device check pending | Visual element capture | On Android 11+, Click editing can capture the previous app once and select an accessibility element by tapping the in-memory image. Verify repeated controls, rotation, protected-window failure, timeout, service disablement, and bitmap disposal; update the Accessibility API declaration and privacy policy before upload. |
| Planned | Upload-key release signing | `bundleRelease` produces an upload-key-signed AAB using credentials outside Git; Play App Signing is enabled. Never commit the keystore or passwords. |
| External | Privacy policy and Data Safety form | Public policy URL and Play declarations accurately describe screen data, package names, clipboard use, local files, exports, and retention. |
| External | Accessibility API declaration | Core purpose, user benefit, activation, collected data, and actions match actual behavior; demonstration video is prepared if requested. |

## P1 - Required Before Production

| Status | Item | Acceptance gate |
| --- | --- | --- |
| Brand check pending | Launcher icon and store identity | Legacy, adaptive, round, and Android themed icon resources compile and are referenced by the release manifest. Confirm the visual identity on physical launchers and approve the permanent app name and application ID. |
| Complete | Explicit backup and transfer exclusions | Android 12+ cloud backup and device transfer plus legacy full backup exclude all internal files, preferences, databases, and external app data; the merged release manifest references both rule sets. |
| Complete | Save-time workflow validity | Incomplete workflows remain explicit drafts; only validator-clean ready workflows can run or be scheduled. Legacy workflows derive their effective state without a format break. |
| Planned | Accessibility and lifecycle device tests | Core flow passes with TalkBack, permission denial/recovery, process restart, and service disablement. |
| In progress | Release lint and bundle gate | Local `test`, `lintRelease`, and unsigned `bundleRelease` pass. Add CI and triage the current 245 warnings before production. |
| External | Store listing package | Phone screenshots, feature graphic, descriptions, support email, content rating, target audience, and app-access answers are complete. |

## P2 - Post-Launch Quality Backlog

- Guided first-run experience and safe example workflows.
- Exportable structured execution failure codes and arguments. Live and run-history presentation is structured and bilingual; diagnostics export remains to be designed.
- Recurring daily and weekly reminders are implemented; battery, timezone, DST, reboot, and notification-recovery behavior still require device testing.
- Broader UI and accessibility instrumentation coverage.
- Complete English and Simplified Chinese coverage for remaining hardcoded Compose surfaces and add locale smoke tests for critical workflows.

## Completed UX Recovery Improvements

- Click, Long Click, Input Text, Tap Coordinates, Swipe, and Image Click share a reusable in-memory capture. The capture remains available until it is replaced, explicitly cleared, the accessibility service stops, or the process ends.
- Image Click defaults to one deterministic best candidate, supports an explicit capped all-matches batch from one initial snapshot, and rechecks target-window safety before every gesture. Template persistence is bounded to a 1024 px long edge and 192 KiB PNG budget with grayscale/downscale optimization; Run history retains only numeric matching and partial-execution diagnostics.
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
| Complete | 7 | Tested system-app workflow packs | An explicit bilingual action idempotently installs 25 Ready workflows across Settings, Clock, Files, and AI Index Finger folders without overwriting user edits. The 21 fixture-free system-app workflows and two self-tests run through the production accessibility service on the API 36 emulator. Two additional Google Clock 9.0 workflows safely modify only a unique, pre-created, disabled `AI_INDEX_FINGER_CLOCK_VALIDATION_V1` fixture: one sets 10:37 and one selects Silent/静音. Dedicated English and Simplified Chinese runtime coverage repeats both workflows, verifies the fixture remains disabled, protects a separate sentinel alarm, and cleans up test fixtures. OEM Clock variants are not claimed as compatible. |
| Complete | 8 | Preflight checks | User-triggered snapshots report validation, Draft/Ready state, variable dataflow, structural and retry-expanded execution limits, launchability, selector match indexes, service state, and notification permission without dispatching actions or writing history. |
| Device check pending | 9 | Date/time scheduling | Users choose an absolute local date and time. Past, DST-gap, and over-one-year targets are rejected; overlap behavior is deterministic; WorkManager remains a best-effort one-time reminder and never executes automation. Verify delivery and timezone-change behavior on devices. |

## Release Validation

1. Run `gradlew.bat test`.
2. Run `gradlew.bat lintRelease`.
3. Increment `versionCode`, confirm `versionName`, and build an upload-key-signed AAB using credentials outside Git.
4. Verify the candidate signature and archive its SHA-256, commit SHA, version, mapping file if minification is enabled, and test report references.
5. Install on at least one API 26 device and one device near target SDK 36.
6. Exercise create, configure, save, reopen, validate, disclose, enable, run, stop, review history, export, import, schedule, deny permissions, and recover permissions.
7. Repeat the critical flow in English and Simplified Chinese, including validation, execution failures, run history, notifications, dialogs, and accessibility descriptions.
8. Upload the exact signed candidate to Play internal testing and resolve pre-launch report crashes, policy warnings, and accessibility findings.

## Rollout Stages

### Internal Testing

Enter only when signing, versioning, policy drafts, CI, and the API 26/API 36 smoke suite are complete. Testers must receive release notes and a known-issues list. Stop promotion for any crash, ANR, data loss, unexpected automation, inaccessible disclosure, broken import/export compatibility, notification regression, or mixed-language critical flow.

### Closed Testing

Use a closed track after internal testing is stable. Include representative Android vendors and accessibility configurations. Require at least seven consecutive days without a release-blocking defect, successful upgrade from the previous candidate, clean-install and process-restart evidence, and resolved Play pre-launch findings. An open-testing phase is optional and should be used only if broader policy or device evidence is needed.

### Production

Start with a staged rollout rather than 100 percent. Suggested progression is 5 percent, 20 percent, 50 percent, then 100 percent, with at least 24 hours and explicit review between stages. Halt rollout for elevated crash/ANR rates, accessibility-policy feedback, data loss, unsafe automation, signature/update failure, broken scheduling, or a material privacy-disclosure mismatch. Resume only with a new incremented `versionCode` candidate and documented verification.

## Release Candidate Record

For each candidate, record:

- Git commit SHA and clean source status.
- `versionName`, `versionCode`, application ID, build time, and Gradle/Java versions.
- Signed AAB SHA-256 and certificate fingerprint; never record signing secrets.
- Test, lint, device-matrix, localization, policy, and pre-launch report outcomes.
- Approved release notes, known issues, rollout owner, start time, and stop decision.

## Next Iteration

Add a protected CI gate, complete remaining English/Simplified Chinese UI coverage, and configure external upload signing. Then verify preflight and one-time/daily/weekly reminder flows on API 26 and a target-SDK 36 device, including notification denial/recovery, missed occurrences, DST-adjacent inputs, timezone changes, process restart, replacement, and cancellation.

## Owner Decisions

- Confirm that user-authored cross-app automation is the product's Google Play core purpose and allowed use case.
- Confirm the permanent application ID `com.aiindexfinger` and signing-key owner.
- Supply the privacy-policy URL, support email, launch regions/languages, listing assets, and Play Console access.