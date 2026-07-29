import { app, BrowserWindow, ipcMain, dialog } from 'electron';
import * as path from 'path';
import * as fsp from 'fs/promises';

// __dirname compatible with both ESM (vite) and CJS (electron-builder/tsc to dist-electron)
declare const __dirname: string;
const { join, dirname } = path;
let mainDir = (typeof __dirname !== 'undefined') ? __dirname : dirname(process.execPath);

let mainWindow: BrowserWindow | null = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    webPreferences: {
      preload: join(mainDir, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    title: 'FlexiBook Editor',
  });

  if (process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else {
    mainWindow.loadFile(join(mainDir, '../dist/index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// File dialogs
ipcMain.handle('dialog:openFile', async (_, options) => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ['openFile'],
    filters: options?.filters || [
      { name: 'JSON', extensions: ['json'] },
      { name: 'All Files', extensions: ['*'] },
    ],
  });
  return result;
});

ipcMain.handle('dialog:saveFile', async (_, options) => {
  const result = await dialog.showSaveDialog(mainWindow!, {
    filters: options?.filters || [
      { name: 'JSON', extensions: ['json'] },
      { name: 'All Files', extensions: ['*'] },
    ],
  });
  return result;
});

ipcMain.handle('dialog:message', async (_, options) => {
  const result = await dialog.showMessageBox(mainWindow!, options);
  return result;
});

// Directory picker for pack export
ipcMain.handle('dialog:openDirectory', async (_, options) => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ['openDirectory', 'createDirectory'],
    ...(options || {}),
  });
  return result;
});

// Secure write of resource pack files (base64 payload)
ipcMain.handle('fs:writePack', async (_, payload: { dir: string; files: { path: string; base64: string }[] }) => {
  try {
    if (!payload || typeof payload.dir !== 'string' || !Array.isArray(payload.files)) {
      throw new Error('Invalid payload for writePack');
    }
    const rootDir = path.resolve(payload.dir);
    for (const entry of payload.files) {
      if (!entry || typeof entry.path !== 'string' || typeof entry.base64 !== 'string') {
        continue;
      }
      const rel = entry.path;
      // Security: reject absolute paths and path traversal
      if (path.isAbsolute(rel) || rel.includes('..')) {
        throw new Error(`Unsafe path rejected: ${rel}`);
      }
      const fullPath = path.join(rootDir, rel);
      const normFull = path.normalize(fullPath);
      const normRoot = path.normalize(rootDir);
      // Ensure the resolved path is still inside the chosen root
      if (normFull !== normRoot && !normFull.startsWith(normRoot + path.sep)) {
        throw new Error(`Path escapes pack root: ${rel}`);
      }
      await fsp.mkdir(path.dirname(fullPath), { recursive: true });
      const buf = Buffer.from(entry.base64, 'base64');
      await fsp.writeFile(fullPath, buf);
    }
    return { ok: true, root: rootDir };
  } catch (err: any) {
    return { ok: false, error: err?.message || String(err) };
  }
});
