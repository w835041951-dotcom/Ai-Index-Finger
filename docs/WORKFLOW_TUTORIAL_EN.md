# AI Index Finger Workflow Tutorial (English)

This guide applies to `0.33.0-beta.12` and helps you create, debug, and run workflows in AI Index Finger.

## 1. Before You Start

- Android 8.0 (API 26) or newer.
- Enable AI Index Finger accessibility service.
- Open your target app once so AI Index Finger can return to it for capture or inspection.

## 2. Create Your First Workflow

1. Open AI Index Finger.
2. Tap New workflow.
3. Name the workflow.
4. Add one step:
   - Delay (for a simple first test), or
   - Global action: Home.
5. Save the workflow.

## 3. Run and Verify

1. Tap Run on your workflow.
2. Confirm execution status in the app and notification.
3. Open Run history to inspect success or failure.

## 4. Build a Practical Workflow

Recommended sequence:

1. Launch app.
2. Wait for node.
3. Click or Input text.
4. Optional If/Else for fallback.
5. Optional Repeat for bounded retries.

Keep early versions small and deterministic.

## 5. Use Element Inspection

1. Tap Inspect recent elements.
2. Identify stable selectors (resource ID preferred).
3. Avoid fragile selectors that depend only on transient text.

If selector uniqueness is ambiguous, adjust conditions before running repeatedly.

## 6. Scheduling Safely

- Schedules are reminders, not silent execution.
- Keep workflows ready-to-run (no validation issues) before scheduling.
- If notification permission is blocked, open notification settings and retry.

## 7. Click by Screenshot

Use **Click by screenshot** only when the target control has no stable accessibility selector. It requires Android 11 or newer and searches only inside visible windows that belong to the selected target app.

1. Capture the target app, draw a recognition area, and choose a click point inside it.
2. The saved template keeps its original crop when possible. A long edge above 1024 px is scaled down; a PNG over 192 KiB is converted to grayscale, then scaled down in 85% steps until it fits. The final template is the only image saved. The selected click point is remapped to the final template.
3. Default matching is **Best match**. It chooses one highest-scoring candidate; equal scores resolve from top to bottom, then left to right. The minimum similarity remains 92% by default and exact 1:1 scale remains the default.
4. Enable **Click all matches** only for a deliberate batch operation. Candidates come from one initial screenshot, run from top to bottom and left to right, default to at most 20 clicks, and never exceed 100. The default interval is 200 ms.

Before every click, the app rechecks the target package, display geometry, target-window set, template footprint, and gesture coordinates. If a click, window check, or timeout fails after an earlier click succeeded, the step records the completed count and does not retry the whole image step. A **Continue** policy preserves this as a warning in Run history.

## 8. Troubleshooting Quick Checks

- Service disabled: enable accessibility service.
- Capture unavailable: requires Android 11+ for visual capture features.
- Workflow cannot run: check readiness issues and failed step details.
- Unexpected target screen: reopen target app and rerun preflight checks.

## 9. Debug with Floating Controls

Choose **Debug** from a Ready workflow on the Home screen. A floating debugger appears above the target app and pauses before the first step.

- Drag the debugger by its title to move it. It snaps to the nearest screen edge.
- Choose **Next** to execute the current step and pause before the following step, including steps inside branches and repeats.
- Choose **Stop** to cancel the debug run without executing a currently paused step.
- The panel temporarily hides while a step executes so it cannot intercept coordinate gestures or appear in image matching. It returns at the next pause.
- The running-workflow notification keeps **Next** and **Stop** available as backup controls.

If Android cannot create the floating debugger, the workflow does not start. Reconnect the automation service and try again.

## 10. Privacy Notes

- No Android `INTERNET` permission; workflow data is not transmitted.
- Workflow data stays on-device.
- Full screenshots used for visual selection stay only in memory and are not uploaded. An image-click action stores its selected crop inside the workflow, and that crop is included when you export the workflow or library.

## 11. Run the Google Clock Validation Workflows

After installing the tested system examples, open the **Clock** folder. The two validation workflows target Google Clock 9.0 (`com.google.android.deskclock`) in the API 36 reference environment. Other Clock versions and OEM apps can use different controls, so run **Preflight** before relying on them.

Prepare a dedicated test alarm before running either workflow:

1. In Google Clock, manually create exactly one alarm named `AI_INDEX_FINGER_CLOCK_VALIDATION_V1`.
2. Turn that alarm off and remove all repeat days. Do not use the validation name for a personal alarm.
3. Run **Set AI_INDEX_FINGER_CLOCK_VALIDATION_V1 to 10:37**.
4. Run **Set AI_INDEX_FINGER_CLOCK_VALIDATION_V1 sound to Silent**.
5. Check Run history. Each workflow reads the saved value back and confirms that the validation alarm remains off.

The workflows do not create, enable, or delete alarms. They stop if the named alarm is missing, duplicated, or enabled, and they do not select an alarm by list position or fixed screen coordinates.

## 12. Next Step

After your first successful run:

1. Add one validation step before each risky action.
2. Add a bounded retry policy instead of unlimited repetition.
3. Export a backup after major edits.

For Chinese screenshots and a full illustrated walkthrough, see:

- WORKFLOW_TUTORIAL_ZH.md
