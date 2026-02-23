import * as vscode from 'vscode';
import * as http from 'http';
import { JetCodeSyncViewProvider } from './webview';

const POLL_ACTIVE_MS = 1000;
const POLL_PAUSED_MS = 5000;
const SCAN_INTERVAL_MS = 30000;
const PORT_RANGE_START = 63340;
const PORT_RANGE_END = 63360;

interface FilePositionResponse {
	filePath: string;
	line: number;
	column: number;
}

interface StatusResponse {
	ideName: string;
	projectName: string;
}

export interface Target {
	port: number;
	ideName: string;
	projectName: string;
}

let pollingTimer: ReturnType<typeof setInterval> | undefined;
let scanTimer: ReturnType<typeof setInterval> | undefined;
let outputChannel: vscode.OutputChannel;
let statusBarItem: vscode.StatusBarItem;
let windowFocused = true;
let connectionAvailable = false;
let syncEnabled = true;
let viewProvider: JetCodeSyncViewProvider;
let positionServer: http.Server | undefined;
let serverPort: number | undefined;
let discoveredTargets: Target[] = [];
let selectedPort: number | undefined;

// Current file position state (1-based line/column, matching the API schema)
let currentPosition: FilePositionResponse = {
	filePath: '',
	line: 1,
	column: 1,
};

function getSelectedEndpointUrl(): string | undefined {
	if (selectedPort === undefined) {
		return undefined;
	}
	return `http://localhost:${selectedPort}/api/filePosition`;
}

export function log(message: string) {
	const timestamp = new Date().toISOString();
	const formatted = `[${timestamp}] ${message}`;
	console.log(formatted);
	outputChannel.appendLine(formatted);
}

function updateStatusBar() {
	if (!syncEnabled || !pollingTimer) {
		statusBarItem.text = '$(sync-ignored) JetCodeSync: Off';
		statusBarItem.tooltip = 'JetCodeSync is stopped. Click to start.';
		statusBarItem.command = 'jetcodesync.start';
		statusBarItem.backgroundColor = undefined;
	} else if (selectedPort === undefined) {
		statusBarItem.text = '$(warning) JetCodeSync: No IDEs Found';
		statusBarItem.tooltip = 'No IDEs detected. Click to rescan.';
		statusBarItem.command = 'jetcodesync.rescan';
		statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
	} else if (windowFocused) {
		statusBarItem.text = '$(debug-pause) JetCodeSync: Paused';
		statusBarItem.tooltip = 'Window is focused — syncing paused. Click to stop.';
		statusBarItem.command = 'jetcodesync.stop';
		statusBarItem.backgroundColor = undefined;
	} else if (!connectionAvailable) {
		statusBarItem.text = '$(warning) JetCodeSync: No Connection';
		statusBarItem.tooltip = 'Cannot reach target IDE. Is it running?';
		statusBarItem.command = 'jetcodesync.stop';
		statusBarItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
	} else {
		statusBarItem.text = '$(sync~spin) JetCodeSync: Syncing';
		statusBarItem.tooltip = 'Actively syncing position from target IDE. Click to stop.';
		statusBarItem.command = 'jetcodesync.stop';
		statusBarItem.backgroundColor = undefined;
	}
	statusBarItem.show();
	notifyWebview();
}

function notifyWebview() {
	if (viewProvider) {
		viewProvider.updateState({
			enabled: syncEnabled,
			connected: connectionAvailable,
			serverUrl: serverPort ? `http://localhost:${serverPort}/api/filePosition` : undefined,
			targets: discoveredTargets,
			selectedPort: selectedPort,
		});
	}
}

// --- HTTP helpers ---

function httpGet(url: string, timeoutMs: number): Promise<string> {
	return new Promise((resolve, reject) => {
		const req = http.get(url, (res) => {
			let data = '';
			res.on('data', (chunk) => { data += chunk; });
			res.on('end', () => {
				if (res.statusCode !== 200) {
					reject(new Error(`HTTP ${res.statusCode}: ${data}`));
					return;
				}
				resolve(data);
			});
		});
		req.on('error', (err) => reject(err));
		req.setTimeout(timeoutMs, () => {
			req.destroy();
			reject(new Error('Request timed out'));
		});
	});
}

function fetchFilePosition(): Promise<FilePositionResponse> {
	const endpoint = getSelectedEndpointUrl();
	if (!endpoint) {
		return Promise.reject(new Error('No target IDE selected'));
	}
	return httpGet(endpoint, 3000).then((data) => JSON.parse(data));
}

// --- Port scanning ---

async function probePort(port: number): Promise<Target | null> {
	try {
		const data = await httpGet(`http://localhost:${port}/api/status`, 1000);
		const status: StatusResponse = JSON.parse(data);
		if (status.ideName) {
			return { port, ideName: status.ideName, projectName: status.projectName || '' };
		}
	} catch {
		// port not available or not a JetCodeSync endpoint
	}
	return null;
}

async function scanPorts() {
	log('Scanning for IDEs...');
	const promises: Promise<Target | null>[] = [];
	for (let port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
		if (port === serverPort) {
			continue;
		}
		promises.push(probePort(port));
	}
	const results = await Promise.all(promises);
	discoveredTargets = results.filter((t): t is Target => t !== null);
	log(`Scan complete: found ${discoveredTargets.length} IDE(s): ${discoveredTargets.map(t => `${t.ideName} — ${t.projectName} (port ${t.port})`).join(', ') || 'none'}`);

	// If current selection is gone, auto-select first available
	if (selectedPort !== undefined && !discoveredTargets.some(t => t.port === selectedPort)) {
		log(`Previously selected port ${selectedPort} no longer available.`);
		selectedPort = discoveredTargets.length > 0 ? discoveredTargets[0].port : undefined;
		if (selectedPort !== undefined) {
			log(`Auto-selected new target: port ${selectedPort}`);
		}
	}
	// If nothing was selected yet, pick first
	if (selectedPort === undefined && discoveredTargets.length > 0) {
		selectedPort = discoveredTargets[0].port;
		log(`Auto-selected target: port ${selectedPort}`);
	}

	updateStatusBar();
}

// --- Sync ---

async function syncPosition() {
	if (windowFocused) {
		log('Window is focused, skipping sync.');
		return;
	}

	if (selectedPort === undefined) {
		log('No target IDE selected, skipping sync.');
		return;
	}

	log('Polling file position...');
	try {
		const position = await fetchFilePosition();
		if (!connectionAvailable) {
			connectionAvailable = true;
			log('Connection restored.');
			updateStatusBar();
		}
		log(`Received: file="${position.filePath}", line=${position.line}, column=${position.column}`);

		if (!position.filePath) {
			log('No file path in response, skipping.');
			return;
		}

		const uri = vscode.Uri.file(position.filePath);
		const doc = await vscode.workspace.openTextDocument(uri);
		log(`Opened document: ${doc.fileName}`);

		const editor = await vscode.window.showTextDocument(doc, { preview: false });
		log(`Shown in editor: ${editor.document.fileName}`);

		// API uses 1-based line/column, VS Code Position is 0-based
		const line = Math.max(0, position.line - 1);
		const column = Math.max(0, position.column - 1);
		const newPosition = new vscode.Position(line, column);
		const newSelection = new vscode.Selection(newPosition, newPosition);
		editor.selection = newSelection;
		editor.revealRange(new vscode.Range(newPosition, newPosition), vscode.TextEditorRevealType.InCenter);
		log(`Caret moved to line ${position.line}, column ${position.column} (0-based: ${line}:${column})`);
	} catch (err: any) {
		if (connectionAvailable) {
			connectionAvailable = false;
			log(`Connection lost: ${err.message}`);
			updateStatusBar();
		} else {
			log(`Still no connection: ${err.message}`);
		}
	}
}

function restartPollingInterval() {
	if (!pollingTimer) {
		return;
	}
	clearInterval(pollingTimer);
	const interval = windowFocused ? POLL_PAUSED_MS : POLL_ACTIVE_MS;
	log(`Polling interval set to ${interval}ms (${windowFocused ? 'paused' : 'active'})`);
	pollingTimer = setInterval(syncPosition, interval);
}

function startPolling() {
	if (pollingTimer) {
		log('Polling already running.');
		return;
	}
	syncEnabled = true;
	const interval = windowFocused ? POLL_PAUSED_MS : POLL_ACTIVE_MS;
	log(`Starting polling every ${interval}ms`);
	pollingTimer = setInterval(syncPosition, interval);
	updateStatusBar();
}

function stopPolling() {
	syncEnabled = false;
	if (pollingTimer) {
		clearInterval(pollingTimer);
		pollingTimer = undefined;
		log('Polling stopped.');
	} else {
		log('Polling was not running.');
	}
	updateStatusBar();
}

// --- Position tracking & HTTP server ---

function updateCurrentPosition(editor: vscode.TextEditor | undefined) {
	if (!editor || editor.document.uri.scheme !== 'file') {
		return;
	}
	const filePath = editor.document.fileName;
	const pos = editor.selection.active;
	// Convert 0-based VS Code position to 1-based API position
	const line = pos.line + 1;
	const column = pos.character + 1;

	if (currentPosition.filePath !== filePath || currentPosition.line !== line || currentPosition.column !== column) {
		currentPosition = { filePath, line, column };
		log(`Position updated: file="${filePath}", line=${line}, column=${column}`);
	}
}

function getOwnStatus(): StatusResponse {
	return {
		ideName: vscode.env.appName,
		projectName: vscode.workspace.name || '',
	};
}

function startPositionServer(): Promise<number> {
	return new Promise((resolve, reject) => {
		let currentPortAttempt = PORT_RANGE_START;

		const server = http.createServer((req, res) => {
			if (req.method === 'GET' && req.url === '/api/filePosition') {
				log(`Server: filePosition request from ${req.socket.remoteAddress}`);
				res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
				res.end(JSON.stringify(currentPosition));
			} else if (req.method === 'GET' && req.url === '/api/status') {
				log(`Server: status request from ${req.socket.remoteAddress}`);
				res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
				res.end(JSON.stringify(getOwnStatus()));
			} else {
				res.writeHead(404, { 'Content-Type': 'application/json' });
				res.end(JSON.stringify({ error: 'Not found' }));
			}
		});

		server.on('error', (err: NodeJS.ErrnoException) => {
			if (err.code === 'EADDRINUSE') {
				currentPortAttempt++;
				if (currentPortAttempt > PORT_RANGE_END) {
					reject(new Error(`All ports in range ${PORT_RANGE_START}-${PORT_RANGE_END} are in use`));
					return;
				}
				log(`Port ${currentPortAttempt - 1} is in use, trying ${currentPortAttempt}...`);
				server.listen(currentPortAttempt, '127.0.0.1');
			} else {
				reject(err);
			}
		});

		server.on('listening', () => {
			const addr = server.address();
			const port = typeof addr === 'object' && addr ? addr.port : 0;
			positionServer = server;
			serverPort = port;
			log(`Position server started on http://localhost:${port}`);
			resolve(port);
		});

		server.listen(currentPortAttempt, '127.0.0.1');
	});
}

function stopPositionServer() {
	if (positionServer) {
		positionServer.close();
		positionServer = undefined;
		serverPort = undefined;
		log('Position server stopped.');
	}
}

export function activate(context: vscode.ExtensionContext) {
	outputChannel = vscode.window.createOutputChannel('JetCodeSync');
	statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
	log('Extension "jetcodesync" activated.');

	// Always assume focused on startup
	windowFocused = true;
	log('Initial window focus state: focused (assumed on startup)');

	// Track current editor position
	updateCurrentPosition(vscode.window.activeTextEditor);

	// Start the position server, then scan for other IDEs
	startPositionServer().then((port) => {
		log(`Position API available at http://localhost:${port}/api/filePosition`);
		log(`Status API available at http://localhost:${port}/api/status`);
		notifyWebview();
		// Initial scan after server is up
		scanPorts();
		// Periodic rescan
		scanTimer = setInterval(scanPorts, SCAN_INTERVAL_MS);
	}).catch((err) => {
		log(`Failed to start position server: ${err.message}`);
	});

	// Register webview provider
	viewProvider = new JetCodeSyncViewProvider(context.extensionUri, {
		onToggleSync: (enabled: boolean) => {
			log(`Webview toggle: ${enabled ? 'enable' : 'disable'}`);
			if (enabled) {
				startPolling();
			} else {
				stopPolling();
			}
		},
		onSelectTarget: (port: number) => {
			log(`Webview target selected: port ${port}`);
			selectedPort = port;
			connectionAvailable = false; // reset until next successful poll
			updateStatusBar();
		},
		onRescan: () => {
			log('Webview rescan requested');
			scanPorts();
		},
		getState: () => ({
			enabled: syncEnabled,
			connected: connectionAvailable,
			serverUrl: serverPort ? `http://localhost:${serverPort}/api/filePosition` : undefined,
			targets: discoveredTargets,
			selectedPort: selectedPort,
		}),
	});

	context.subscriptions.push(
		vscode.window.registerWebviewViewProvider('jetcodesync.configPanel', viewProvider),
		// Track caret movement
		vscode.window.onDidChangeTextEditorSelection((e) => {
			updateCurrentPosition(e.textEditor);
		}),
		// Track active editor changes (tab switches, file opens)
		vscode.window.onDidChangeActiveTextEditor((editor) => {
			if (editor) {
				log(`Active editor changed: ${editor.document.fileName}`);
				updateCurrentPosition(editor);
			}
		}),
		vscode.window.onDidChangeWindowState((state) => {
			windowFocused = state.focused;
			log(`Window focus changed: ${windowFocused ? 'focused (pausing sync)' : 'unfocused (resuming sync)'}`);
			updateStatusBar();
			restartPollingInterval();
			if (!windowFocused && pollingTimer) {
				syncPosition();
			}
		}),
		vscode.commands.registerCommand('jetcodesync.start', () => {
			log('Command: jetcodesync.start');
			startPolling();
			vscode.window.showInformationMessage('JetCodeSync: Started syncing.');
		}),
		vscode.commands.registerCommand('jetcodesync.stop', () => {
			log('Command: jetcodesync.stop');
			stopPolling();
			vscode.window.showInformationMessage('JetCodeSync: Stopped syncing.');
		}),
		vscode.commands.registerCommand('jetcodesync.rescan', () => {
			log('Command: jetcodesync.rescan');
			scanPorts();
			vscode.window.showInformationMessage('JetCodeSync: Rescanning for IDEs...');
		}),
		vscode.commands.registerCommand('jetcodesync.openPanel', () => {
			vscode.commands.executeCommand('jetcodesync.configPanel.focus');
		}),
		statusBarItem,
		outputChannel
	);

	// Auto-start polling on activation
	startPolling();
}

export function deactivate() {
	stopPolling();
	if (scanTimer) {
		clearInterval(scanTimer);
		scanTimer = undefined;
	}
	stopPositionServer();
	log('Extension "jetcodesync" deactivated.');
}
