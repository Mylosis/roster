# [Roster](https://github.com/Mylosis/roster)

A RuneLite plugin for managing multiple OSRS accounts. Organize your mains, alts, and pures into color-coded categories, switch between them with a single click, and have everything cloud-sync across your machines via your RuneLite account. Inspired by [Profiles Plugin.](https://runelite.net/plugin-hub/show/profiles-panel)

## Features

- **Account Management**: Create, edit, and organize multiple OSRS accounts
- **Fluid Management**: Organize accounts into color-coded, collapsible categories with drag-and-drop reordering
- **Category Colors**: Auto-assigned color palette with custom color picker support
- **Grid & List Views**: Toggle between detailed list view and compact two-column grid
- **Import/Export**: Backup and transfer accounts via clipboard (per-account or bulk)
- **Privacy Controls**: Individually hide login names, aliases, or notes
- **Online Indicator**: Green dot shows which account is logged in on the current client
- **Search**: Filter accounts by name, username, or notes
- **Drag & Drop**: Reorder accounts and move them between categories
- **Account Type Badges**: Mark each account as Ironman, HCIM, UIM, GIM, UGIM, Skiller, Pure, or Main — click any badge to change it on the fly
- **Sort Options**: Sort each list by name, last online, date added, or your manual drag order
- **Last Online**: Cards show when each account was last seen logged in on this client
- **Cloud Sync**: Account data syncs across your machines via your RuneLite account (with a local backup retained too)

## Usage

### Adding Accounts
1. Open the Roster panel from the sidebar
2. Click "+ Account" to add a new account
3. Enter the account details:
   - **Login**: Login email or username (required)
   - **Alias**: Display name (optional)
   - **Category**: Assign to a category (optional)
   - **Notes**: Any additional notes

### Managing Categories
- Click "+ Category" to create a new category
- Drag accounts between categories
- Click category headers to collapse/expand
- Use the three-dot menu for rename, color, and delete options

### Import/Export
- Click the clipboard icon in the footer
- **Import from Clipboard**: Paste JSON account data
- **Export to Clipboard**: Copy all accounts as JSON
- Per-account export available via the three-dot menu on each account

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Grid View | Compact two-column grid layout | Off |
| Sort accounts by | Manual / Name / Last online / Date added | Manual |
| Hide Login | Hide login/username on cards | Off |
| Hide Notes | Hide notes on cards | Off |
| Hide Alias | Hide alias, show login only | Off |
| Hide Last Online | Hide the "logged in X ago" stamp on cards | Off |
| Confirm Delete | Confirmation dialog for deletions | On |
| Import Handling | How to handle duplicate imports (Skip/Overwrite/Add Suffix) | Skip |

## Installation

### From Plugin Hub (Recommended)
1. Open RuneLite
2. Click the wrench icon to open Configuration
3. Click "Plugin Hub" at the bottom
4. Search for "Roster"
5. Click Install

### Manual Installation
1. Download the latest release JAR from Releases
2. Place it in your RuneLite plugins folder
3. Restart RuneLite

## Building from Source

```bash
# Clone the repository
git clone https://github.com/Mylosis/roster.git
cd roster

# Build with Gradle
./gradlew build

# Run with RuneLite (development)
./gradlew run
```

Requires JDK 11 or higher.

## License

BSD 2-Clause License

## Changelog

### v1.1.1
- **Fix: account data loss on first v1.1 upgrade.** The migration in v1.1.0 renamed `roster.json` to a backup file after handing the data to ConfigManager. ConfigManager batches its writes, so if RuneLite was closed before its debounced flush hit disk, the in-memory data was lost — and the file backup had been renamed away. Migration is now **idempotent**: the file is never renamed, so a future ConfigManager loss can always re-seed from the same place. Recovery path checks `roster.json` → `roster.backup.json` → `roster.json.pre-v1.1.bak` so users who already hit the v1.1.0 bug get their data back automatically on the next launch.
- **Account type icons** — replaced the letter-in-circle badges with the actual OSRS sprites: official chat badges for the six Ironman variants, an Abyssal whip for Main, the in-game Skills tab icon for Skiller, and a Granite maul for Pure.
- Badges are larger (16 → 20px in list view, 12 → 16px in grid view) for legibility.
- More breathing room between display name and the type badge.
- Footer menu labels tidied to match the per-account menu: "Import from Clipboard" / "Export to Clipboard".

### v1.1.0
- **Cloud sync** — account data now persists through RuneLite's ConfigManager, so it travels with your RuneLite account across machines. Existing `roster.json` data is migrated automatically on first launch (original file preserved as `roster.json.pre-v1.1.bak`).
- **Account type badges** — small clickable badge on each card shows whether the account is an Ironman variant, Skiller, Pure, etc. New accounts pick a type from a dropdown in the Add form; existing accounts can be changed by clicking the badge directly.
- **Sort options** — new ⇵ button in the header lets you sort accounts by name, last online, or date added. Manual sort preserves your hand-arranged drag order.
- **Last online tracking** — cards show "logged in X ago" for accounts that aren't currently online, auto-stamped when an account logs in on the current client.
- New privacy toggle to hide the last-online stamp.

### v1.0.0
- Initial release
- Account management with color-coded categories
- Grid and list view modes
- Drag-and-drop reordering
- Clipboard import/export (bulk and per-account)
- Privacy controls (hide login, notes, alias)
- Search and filtering
- Online status indicator
- All data stored locally
