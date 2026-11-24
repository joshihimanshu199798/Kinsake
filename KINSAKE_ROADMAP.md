# Kinsake Complete Feature Roadmap

This roadmap is the implementation contract for the full Kinsake feature list. Work is local-first and privacy-first. Existing web and Android functionality must remain intact.

## Current architecture audit

- Web: React 18, TypeScript, Vite, IndexedDB media store, localStorage profile, JSON backup/restore.
- Android: Kotlin, Jetpack Compose, Room/SQLite metadata, app-private media files, Android Photo Picker and voice recording.
- No server, cloud sync, AI provider, guest-link service, face-recognition engine, or encryption/key-management layer is currently configured.
- Android changes are currently carried by the native-app PR and its build-fix PR. Do not remove those changes when integrating this roadmap.

## Delivery phases

### Phase 1 — Timeline foundation and safe metadata
- Shared calendar-age calculation at each memory's capture date.
- Age badges for photos, voice notes and milestones.
- Timeline ordering and density controls that preserve source aspect ratios.
- "On This Day" anniversary spotlight.
- Extend memory metadata for captions, favourites, tags, people, mood, folder and trash state.
- Preserve original media and existing JSON backups during schema evolution.

### Phase 2 — Vault, file types and recovery
- Nested folders and multi-format file records (photo, video, audio, PDF and scan).
- Recycle Bin with a 30-day recovery window.
- Batch metadata/EXIF editing and junk-drawer classification.
- Versioned full archive export/restore, including media and growth records.
- [x] Android ZIP backup/restore: exports photos, voice recordings, milestones and child birth date to a user-selected document; restore validates the archive, stages media, and merges records without clearing existing data.
- [ ] Add archive encryption/password protection and automated round-trip tests before treating the archive as secure long-term storage.

### Phase 3 — Parenting and family journal
- Multiple child profiles and per-child age labels.
- Milestones, ceremonies, quotes and family journal.
- Growth measurements with clearly sourced WHO/IAP reference data and age/sex selection where required.
- Vaccination records and reminders.
- Time-capsule items with local unlock dates.
- Voice notes attached to memory records.

### Phase 4 — Photo studio and discovery
- Non-destructive edit recipes and original/edited comparison.
- Slideshow and print-ready photobook export.
- Local duplicate/blur review tools.
- OCR and semantic search only after a privacy-preserving on-device implementation or explicit opt-in provider integration is selected.
- Gemini captions and face clustering are not enabled by default; they require explicit consent, provider configuration, and clear disclosure about any media leaving the device.

### Phase 5 — Family sharing and security
- Family-circle permissions and guest-safe mode.
- PIN-protected expiring share links require a secure hosting/backend service; they cannot be safely implemented as static local-only links.
- Download controls and watermarking are deterrents, not guarantees against screenshots or copying.
- Biometric/PIN lock and decoy vault with platform-appropriate secure key storage.
- Encrypted archive export and restore.

## Non-negotiable acceptance criteria

1. Existing memories and backups remain readable after schema changes.
2. Original media is never overwritten by edits.
3. No photo, audio, document, face data or OCR text is sent off-device without an explicit, informed user action.
4. Deletion is recoverable for 30 days before permanent purge.
5. Android database migrations are explicit; no destructive Room fallback.
6. Growth charts identify the reference standard and do not present medical advice.
7. CI build success is not represented as physical-device testing.
8. Every phase ships behind a reviewable pull request with build/test results and known limitations.
