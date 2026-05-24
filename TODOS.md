# Roster — Backlog

Working list of ideas and known issues. Pick from here when planning a release.
Each item gets fleshed into a proper spec under `docs/superpowers/specs/` before
implementation.

## 🚨 Known issues / correctness

### Account data does not sync via RuneLite account
**Status:** Confirmed bug.
[AccountStorage.java:44-46](src/main/java/com/mylosis/roster/storage/AccountStorage.java)
writes `roster.json` directly to `RuneLite.RUNELITE_DIR` (the `.runelite` folder
root), bypassing `ConfigManager` entirely. Only the toggles declared via
`@ConfigItem` in `RosterConfig` (gridView, hideLogin, etc.) ride RL's cloud sync.
The actual account list is local-only — users who set up Roster on PC A see an
empty plugin on PC B.

**Fix:** persist `AccountData` through `ConfigManager.setConfiguration("roster",
key, value)` instead of (or in addition to) the file. ConfigManager serializes
complex objects via Gson and respects the active profile's cloud-sync flag.
Needs a one-time migration that reads the existing `roster.json`, writes it
through ConfigManager, and renames the old file so we don't double-import on
the next launch. README's "or synced with RL" line should also be corrected
(or made true by this fix).

---

## ✅ Shipped in v1.1

- **Cloud sync** — `AccountData` persists through `ConfigManager`, migration from `roster.json` is automatic and idempotent. README "synced with RL" line is now accurate.
- **Account type badges** — small clickable badge on each card; dropdown in the Add form for new accounts; badge popover for changing existing ones; same swatch shows up in both places.
- **Sort options** — `SortKey` enum, `sortKey` ConfigItem, header ⇵ button popup. Drag-while-non-manual saves the new manual order silently and surfaces a one-time toast so the lack of visible movement doesn't read as broken.
- **Last-online tracking** — `lastOnlineAt` field, 60s throttle on stamping, hidden while account is currently online, `hideLastOnline` privacy toggle.

---

## 🧊 Parking lot (not chosen for v1.1 but worth keeping)

- **Multi-select + bulk actions** — cut from v1.1. Shift/ctrl-click to select N
  accounts; floating action bar with bulk move/delete/export. Revisit later if
  account counts grow enough that one-by-one ops become painful.
- Click-to-switch RuneLite profile (killer feature, needs API spike)
- Import from RuneLite's own profile list (great first-run onboarding)
- Pinned / favourite accounts
- Tags as a second axis alongside categories
- Category icons (alongside colour)
- Stats strip: "12 accounts · 4 categories · 3 online"
- Goal/progress field per account ("92→99 Slayer")
- Encrypted notes
- CSV file export
- Backup reminder nudges
