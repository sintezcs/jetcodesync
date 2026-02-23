# JetCodeSync

Sync your cursor position and open files across JetBrains IDEs and VS Code in real time.

When you switch from one IDE to another, JetCodeSync automatically opens the same file and moves the cursor to the same position you were at. It works across any combination of JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, etc.) and VS Code (with the companion extension).

<!-- Plugin description -->
JetCodeSync synchronizes your cursor position and active file across multiple IDEs in real time.

**How it works:**
- Tracks your current file, cursor line, and column in each IDE
- Exposes position data via a local REST API
- When you switch away from one IDE and return to another, the target IDE automatically navigates to the file and position you were last editing

**Features:**
- Auto-discovery of running JetCodeSync instances on the local machine (ports 63340-63359)
- Dropdown selector to choose which IDE to sync with
- Enable/disable sync with a single checkbox
- Status indicator showing sync state (Idle, Syncing, Error)
- REST API endpoints for integration with VS Code and other editors
- Zero configuration required — just install and go

**REST API Endpoints:**
- `GET /api/filePosition` — returns the current file path, line, and column as JSON
- `GET /api/status` — returns the IDE name and currently open project name
<!-- Plugin description end -->

## How It Works

```
  JetBrains IDE A                         JetBrains IDE B / VS Code
  +-----------------+                     +-----------------+
  | Tracks cursor   |                     | Tracks cursor   |
  | position        |                     | position        |
  |                 |   REST API polling   |                 |
  | /api/filePos <--|---------------------|-- polls when    |
  | /api/status  <--|---------------------|   IDE B is      |
  |                 |                     |   in focus      |
  +-----------------+                     +-----------------+
```

1. Each IDE instance runs a local HTTP server exposing `/api/filePosition` and `/api/status`
2. When you switch away from an IDE (it loses focus), it starts polling the selected sync target
3. When you return, it navigates to the last known position from the other IDE
4. Instances auto-discover each other by scanning ports 63340-63359

## Installation

### From Disk

1. Download the latest release ZIP from [Releases](https://github.com/sintezcs/jetcodesync/releases/latest)
2. Open your JetBrains IDE
3. Go to **Settings** > **Plugins** > **gear icon** > **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart the IDE

### Building from Source

```bash
./gradlew buildPlugin
```

The plugin ZIP will be at `build/distributions/jetcodesync-*.zip`.

## Usage

1. Install JetCodeSync in two or more JetBrains IDEs (or pair with the VS Code companion extension)
2. Open the **JetCodeSync** tool window (right sidebar)
3. The plugin automatically discovers other running instances
4. Select a sync target from the dropdown (or leave the default)
5. Switch between your IDEs — the cursor position follows you

### Tool Window

The JetCodeSync panel (right sidebar) shows:

- **Sync status** — current state (Idle, Syncing, No target found)
- **Enable sync** — checkbox to toggle synchronization on/off
- **Sync Target** — dropdown listing discovered IDE instances with their project names
- **Rescan** — re-scans ports to find new or closed IDE instances
- **This IDE** — the port and endpoint URL of the current IDE's REST API

### REST API

#### `GET /api/filePosition`

Returns the current cursor position:

```json
{
  "filePath": "/Users/you/project/src/main.kt",
  "line": 42,
  "column": 10
}
```

#### `GET /api/status`

Returns IDE identification:

```json
{
  "ideName": "IntelliJ IDEA 2025.2.5",
  "projectName": "my-project"
}
```

## Development

### Building

```bash
./gradlew build            # Build and run tests
./gradlew buildPlugin      # Build distributable ZIP
./gradlew runIde           # Launch a sandbox IDE with the plugin
```

### Project Structure

```
src/main/kotlin/com/github/sintezcs/jetcodesync/
├── http/
│   ├── FilePositionRestService.kt   # /api/filePosition endpoint
│   └── IdeStatusRestService.kt      # /api/status endpoint
├── listeners/
│   ├── CaretPositionListener.kt     # Tracks cursor movement
│   ├── FileEditorListener.kt        # Tracks tab/file switches
│   ├── FileOpenedListener.kt        # Tracks file open events
│   ├── SelectionPositionListener.kt # Tracks text selection
│   └── WindowFocusListener.kt       # Detects IDE focus changes
├── services/
│   ├── FilePositionStateService.kt  # Current position state (thread-safe)
│   ├── IdeDiscoveryService.kt       # Scans for other IDE instances
│   ├── SyncSettingsService.kt       # Persisted settings
│   └── VsCodePollingService.kt      # Polls remote IDE for position
├── startup/
│   └── MyProjectActivity.kt         # Plugin initialization
└── toolWindow/
    └── MyToolWindowFactory.kt       # Tool window UI
```

## Requirements

- JetBrains IDE 2025.2.5 or later (IntelliJ IDEA, PyCharm, WebStorm, GoLand, etc.)
- Java 21+
