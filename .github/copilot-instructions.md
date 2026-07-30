# Repository Development Instructions

## Bilingual Product Requirement

All new user-facing interfaces and features must support both English and Simplified Chinese from their first implementation.

- Treat English and Simplified Chinese as equally required product languages. A feature is incomplete if either translation is missing.
- Store default English strings in `app/src/main/res/values/strings.xml` and Simplified Chinese strings in `app/src/main/res/values-zh-rCN/strings.xml`.
- Never hardcode user-visible text in Compose, XML layouts, activities, services, workers, notifications, dialogs, accessibility descriptions, errors, validation messages, onboarding, or tests. Use Android string resources and `stringResource`, `context.getString`, or the corresponding resource API.
- Add matching resource keys to both language files in the same change. Keep placeholders, quantities, punctuation intent, and formatting arguments compatible between translations.
- Use plurals and formatted string resources where grammar or counts vary. Do not assemble translated sentences from fragments.
- Keep brand names, package names, resource IDs, serialized enum values, JSON keys, file formats, and protocol identifiers unchanged unless the product requirement explicitly says otherwise.
- Localize enum and status labels through presentation-layer resource mappings. Do not change persisted or serialized values just to translate their display text.
- Ensure notifications, background-work messages, import/export failures, validation feedback, accessibility labels, and content descriptions are bilingual, not only visible Compose screens.
- When adding or changing user-visible behavior, test or otherwise verify both English and Simplified Chinese resource variants. Android resource compilation and relevant unit/UI tests must pass.
- During review, flag missing English or Simplified Chinese resources as a blocking product defect.