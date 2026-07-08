# Implementation Plan

## Priority: Critical
1. **Participant Comparison Screen:** Build a new Jetpack Compose screen that allows selecting two participants to compare their message counts, media shared, active hours, and sentiment scores.

## Priority: High
2. **Validation Reports Dashboard:** Add logic to save the import mismatch details (missing members, duplicate members) to a `ValidationReport` database entity for post-import auditing.
3. **Manual Participant Mapping:** Allow users to manually link an imported member to an existing chat participant if the automatic phone/name normalization fails.

## Priority: Medium
4. **Duplicate Report Prevention:** Cache report hashes and generation timestamps to avoid re-exporting the exact same PDF/HTML if no new messages were added.
5. **Advanced Search Filters:** Extend the message viewer to filter specifically by AI-detected topics or precise date ranges.

## Priority: Low
6. **Background Worker Resilience:** Utilize WorkManager for exports and large imports instead of simple coroutines if the app is killed.
7. **Accessibility Audits:** Review content descriptions on dynamic charts.
