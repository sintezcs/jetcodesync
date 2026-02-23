# JetCodeSync for VS Code

Sync your cursor position and open files between VS Code and JetBrains IDEs in real time.

When you switch from VS Code to a JetBrains IDE (or vice versa), JetCodeSync automatically opens the same file and moves the cursor to the same position you were editing. Works with IntelliJ IDEA, PyCharm, WebStorm, GoLand, and any other JetBrains IDE running the JetCodeSync plugin.

## How It Works

1. The extension runs a local HTTP server exposing your current file and cursor position
2. It auto-discovers other JetCodeSync instances (JetBrains IDEs or other VS Code windows) by scanning ports 63340-63359
3. When VS Code loses focus, it starts polling the selected sync target for position updates
4. When you return to VS Code, it navigates to the last known position from the other IDE
5. When another IDE polls VS Code, it serves the current position back

## Features

- **Auto-discovery** — automatically finds running JetBrains IDEs and other VS Code instances with JetCodeSync
- **Bidirectional sync** — both serves and consumes position data via REST API
- **Focus-aware** — only syncs when VS Code is unfocused, pauses when you're actively editing
- **Status bar indicator** — shows current sync state (Syncing, Paused, No Connection, Off)
- **Sidebar panel** — configure sync target, enable/disable sync, and rescan for IDEs
- **Zero configuration** — install and go, targets are detected automatically

## Installation

### From VSIX

1. Download the `.vsix` file from [Releases](https://github.com/sintezcs/jetcodesync/releases/latest)
2. Open VS Code
3. Run **Extensions: Install from VSIX...** from the Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`)
4. Select the downloaded `.vsix` file

### Building from Source

```bash
cd vscode/jetcodesync
npm install
npm run compile
```

To package as VSIX:

```bash
npx @vscode/vsce package
```

## Usage

1. Install the JetCodeSync plugin in your JetBrains IDE and this extension in VS Code
2. Both will auto-discover each other on startup
3. Open the **JetCodeSync** panel in the Activity Bar (left sidebar) to see detected IDEs
4. Select a sync target from the dropdown (the first one is selected by default)
5. Switch between IDEs — the cursor position follows you

### Sidebar Panel

Click the JetCodeSync icon in the Activity Bar to open the settings panel:

- **Enable/Disable** — toggle sync on or off
- **Sync Target** — dropdown listing discovered IDE instances with project names
- **Rescan** — re-scan ports to find new or recently closed IDE instances
- **Server URL** — shows the local endpoint URL that other IDEs can poll

### Status Bar

The status bar item (bottom left) shows the current state:

| Status | Meaning |
|--------|---------|
| **Syncing** | Actively polling the target IDE (VS Code is unfocused) |
| **Paused** | VS Code is focused, sync is paused |
| **No Connection** | Target IDE is not reachable |
| **No IDEs Found** | No JetCodeSync instances detected on scanned ports |
| **Off** | Sync is manually disabled |

Click the status bar item to start/stop syncing or rescan.

### Commands

Open the Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`) and type "JetCodeSync":

| Command | Description |
|---------|-------------|
| `JetCodeSync: Start Syncing` | Enable sync and start polling |
| `JetCodeSync: Stop Syncing` | Disable sync and stop polling |
| `JetCodeSync: Rescan for IDEs` | Re-scan port range for IDE instances |
| `JetCodeSync: Open Settings Panel` | Open the sidebar settings panel |

## REST API

The extension runs a local HTTP server on an available port in the range 63340-63359.

### `GET /api/filePosition`

Returns the current cursor position:

```json
{
  "filePath": "/Users/you/project/src/app.ts",
  "line": 42,
  "column": 10
}
```

### `GET /api/status`

Returns VS Code identification:

```json
{
  "ideName": "Visual Studio Code",
  "projectName": "my-project"
}
```

## Companion Plugin

For this extension to work, you need the [JetCodeSync plugin](../../README.md) installed in your JetBrains IDE. Both the VS Code extension and the JetBrains plugin use the same REST API protocol and port scanning range, so they discover each other automatically.

## Requirements

- VS Code 1.105.0 or later
- A JetBrains IDE with the JetCodeSync plugin installed (for cross-IDE sync)
