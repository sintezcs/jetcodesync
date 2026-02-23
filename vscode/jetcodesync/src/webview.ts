import * as vscode from 'vscode';

export interface SyncState {
	enabled: boolean;
	connected: boolean;
	serverUrl?: string;
	targets: Array<{ port: number; ideName: string; projectName: string }>;
	selectedPort?: number;
}

export interface WebviewCallbacks {
	onToggleSync: (enabled: boolean) => void;
	onSelectTarget: (port: number) => void;
	onRescan: () => void;
	getState: () => SyncState;
}

export class JetCodeSyncViewProvider implements vscode.WebviewViewProvider {
	private _view?: vscode.WebviewView;

	constructor(
		private readonly _extensionUri: vscode.Uri,
		private readonly _callbacks: WebviewCallbacks,
	) {}

	public resolveWebviewView(
		webviewView: vscode.WebviewView,
		_context: vscode.WebviewViewResolveContext,
		_token: vscode.CancellationToken,
	) {
		this._view = webviewView;

		webviewView.webview.options = {
			enableScripts: true,
		};

		webviewView.webview.html = this._getHtmlForWebview();

		webviewView.webview.onDidReceiveMessage((message) => {
			switch (message.type) {
				case 'toggleSync':
					this._callbacks.onToggleSync(message.enabled);
					break;
				case 'selectTarget':
					this._callbacks.onSelectTarget(message.port);
					break;
				case 'rescan':
					this._callbacks.onRescan();
					break;
				case 'requestState':
					this.updateState(this._callbacks.getState());
					break;
			}
		});
	}

	public updateState(state: SyncState) {
		if (this._view) {
			this._view.webview.postMessage({ type: 'stateUpdate', ...state });
		}
	}

	private _getHtmlForWebview(): string {
		return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
	body {
		padding: 12px;
		font-family: var(--vscode-font-family);
		font-size: var(--vscode-font-size);
		color: var(--vscode-foreground);
	}
	.section {
		margin-bottom: 16px;
	}
	label {
		display: block;
		margin-bottom: 4px;
		font-weight: 600;
		font-size: 11px;
		text-transform: uppercase;
		color: var(--vscode-descriptionForeground);
	}
	select {
		width: 100%;
		box-sizing: border-box;
		padding: 4px 8px;
		background: var(--vscode-dropdown-background);
		color: var(--vscode-dropdown-foreground);
		border: 1px solid var(--vscode-dropdown-border, transparent);
		border-radius: 2px;
		font-family: var(--vscode-font-family);
		font-size: var(--vscode-font-size);
	}
	select:focus {
		outline: 1px solid var(--vscode-focusBorder);
		border-color: var(--vscode-focusBorder);
	}
	button {
		margin-top: 6px;
		padding: 4px 14px;
		background: var(--vscode-button-background);
		color: var(--vscode-button-foreground);
		border: none;
		border-radius: 2px;
		cursor: pointer;
		font-size: var(--vscode-font-size);
	}
	button:hover {
		background: var(--vscode-button-hoverBackground);
	}
	.secondary-btn {
		background: var(--vscode-button-secondaryBackground, #3a3d41);
		color: var(--vscode-button-secondaryForeground, #ccc);
	}
	.secondary-btn:hover {
		background: var(--vscode-button-secondaryHoverBackground, #45494e);
	}
	.toggle-row {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.toggle-row input[type="checkbox"] {
		accent-color: var(--vscode-button-background);
		width: 16px;
		height: 16px;
		cursor: pointer;
	}
	.toggle-label {
		font-size: var(--vscode-font-size);
		cursor: pointer;
		text-transform: none;
		font-weight: normal;
		color: var(--vscode-foreground);
	}
	.status-indicator {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 8px;
		border-radius: 4px;
		background: var(--vscode-textBlockQuote-background);
	}
	.status-dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		flex-shrink: 0;
	}
	.status-dot.connected {
		background: #3fb950;
	}
	.status-dot.disconnected {
		background: #f85149;
	}
	.status-dot.disabled {
		background: #8b949e;
	}
	.status-text {
		font-size: var(--vscode-font-size);
	}
	.server-url {
		padding: 6px 8px;
		background: var(--vscode-textBlockQuote-background);
		border-radius: 4px;
		font-family: var(--vscode-editor-font-family);
		font-size: var(--vscode-editor-font-size);
		word-break: break-all;
		user-select: all;
	}
	.no-targets {
		padding: 8px;
		color: var(--vscode-descriptionForeground);
		font-style: italic;
		font-size: var(--vscode-font-size);
	}
</style>
</head>
<body>
	<div class="section">
		<label>Sync Status</label>
		<div class="status-indicator">
			<div class="status-dot disabled" id="statusDot"></div>
			<span class="status-text" id="statusText">Initializing...</span>
		</div>
	</div>

	<div class="section">
		<div class="toggle-row">
			<input type="checkbox" id="syncToggle" checked>
			<label for="syncToggle" class="toggle-label">Enable sync</label>
		</div>
	</div>

	<div class="section">
		<label>Sync From</label>
		<select id="targetSelect"></select>
		<div class="no-targets" id="noTargets" style="display: none;">No IDEs detected. Click Rescan.</div>
		<button class="secondary-btn" id="rescanBtn">Rescan</button>
	</div>

	<div class="section" id="serverSection" style="display: none;">
		<label>This IDE's Endpoint</label>
		<div class="server-url" id="serverUrl"></div>
	</div>

<script>
	const vscode = acquireVsCodeApi();

	const syncToggle = document.getElementById('syncToggle');
	const targetSelect = document.getElementById('targetSelect');
	const noTargets = document.getElementById('noTargets');
	const rescanBtn = document.getElementById('rescanBtn');
	const statusDot = document.getElementById('statusDot');
	const statusText = document.getElementById('statusText');
	const serverSection = document.getElementById('serverSection');
	const serverUrlEl = document.getElementById('serverUrl');

	syncToggle.addEventListener('change', function() {
		vscode.postMessage({ type: 'toggleSync', enabled: syncToggle.checked });
	});

	targetSelect.addEventListener('change', function() {
		var port = parseInt(targetSelect.value, 10);
		if (!isNaN(port)) {
			vscode.postMessage({ type: 'selectTarget', port: port });
		}
	});

	rescanBtn.addEventListener('click', function() {
		vscode.postMessage({ type: 'rescan' });
	});

	window.addEventListener('message', function(event) {
		var msg = event.data;
		if (msg.type === 'stateUpdate') {
			syncToggle.checked = msg.enabled;

			// Update status
			statusDot.className = 'status-dot';
			if (!msg.enabled) {
				statusDot.classList.add('disabled');
				statusText.textContent = 'Disabled';
			} else if (!msg.targets || msg.targets.length === 0) {
				statusDot.classList.add('disconnected');
				statusText.textContent = 'No IDEs detected';
			} else if (msg.connected) {
				statusDot.classList.add('connected');
				var connTarget = msg.targets.find(function(t) { return t.port === msg.selectedPort; });
				statusText.textContent = connTarget
					? 'Connected to ' + connTarget.ideName
					: 'Connected';
			} else {
				statusDot.classList.add('disconnected');
				statusText.textContent = 'Cannot reach target IDE';
			}

			// Update targets dropdown
			var targets = msg.targets || [];
			targetSelect.innerHTML = '';
			if (targets.length === 0) {
				targetSelect.style.display = 'none';
				noTargets.style.display = 'block';
			} else {
				targetSelect.style.display = '';
				noTargets.style.display = 'none';
				for (var i = 0; i < targets.length; i++) {
					var t = targets[i];
					var opt = document.createElement('option');
					opt.value = String(t.port);
					var label = t.ideName;
					if (t.projectName) {
						label += ' \\u2014 ' + t.projectName;
					}
					label += ' (port ' + t.port + ')';
					opt.textContent = label;
					if (t.port === msg.selectedPort) {
						opt.selected = true;
					}
					targetSelect.appendChild(opt);
				}
			}

			// Update server URL
			if (msg.serverUrl) {
				serverSection.style.display = 'block';
				serverUrlEl.textContent = msg.serverUrl;
			} else {
				serverSection.style.display = 'none';
			}
		}
	});

	// Request initial state
	vscode.postMessage({ type: 'requestState' });
</script>
</body>
</html>`;
	}
}
