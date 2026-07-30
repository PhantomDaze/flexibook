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
    ...(options?.defaultPath ? { defaultPath: options.defaultPath } : {}),
  });
  return result;
});

ipcMain.handle('dialog:saveFile', async (_, options) => {
  const result = await dialog.showSaveDialog(mainWindow!, {
    filters: options?.filters || [
      { name: 'JSON', extensions: ['json'] },
      { name: 'All Files', extensions: ['*'] },
    ],
    ...(options?.defaultPath ? { defaultPath: options.defaultPath } : {}),
  });
  return result;
});

ipcMain.handle('dialog:message', async (_, options) => {
  const result = await dialog.showMessageBox(mainWindow!, options);
  return result;
});

/** Read a UTF-8 text file after open dialog. Returns content + path, or canceled. */
ipcMain.handle('fs:readTextFile', async (_, filePath: string) => {
  try {
    if (!filePath || typeof filePath !== 'string') {
      return { ok: false, error: 'Invalid path' };
    }
    const resolved = path.resolve(filePath);
    const text = await fsp.readFile(resolved, 'utf8');
    return { ok: true, path: resolved, text };
  } catch (err: any) {
    return { ok: false, error: err?.message || String(err) };
  }
});

/** Write UTF-8 text to a path chosen via save dialog. */
ipcMain.handle('fs:writeTextFile', async (_, payload: { path: string; text: string }) => {
  try {
    if (!payload || typeof payload.path !== 'string' || typeof payload.text !== 'string') {
      return { ok: false, error: 'Invalid payload' };
    }
    const resolved = path.resolve(payload.path);
    await fsp.mkdir(path.dirname(resolved), { recursive: true });
    await fsp.writeFile(resolved, payload.text, 'utf8');
    return { ok: true, path: resolved };
  } catch (err: any) {
    return { ok: false, error: err?.message || String(err) };
  }
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

const PACK_READ_MAX_FILES = 500;
const PACK_READ_MAX_FILE_BYTES = 12 * 1024 * 1024; // 12 MiB per file
const PACK_READ_MAX_TOTAL_BYTES = 48 * 1024 * 1024; // 48 MiB total

async function walkPackFiles(
  rootDir: string,
  dir: string,
  out: { path: string; base64: string }[],
  totals: { bytes: number },
): Promise<void> {
  const entries = await fsp.readdir(dir, { withFileTypes: true });
  for (const ent of entries) {
    if (out.length >= PACK_READ_MAX_FILES) {
      throw new Error(`Pack has too many files (max ${PACK_READ_MAX_FILES})`);
    }
    const full = path.join(dir, ent.name);
    const rel = path.relative(rootDir, full).split(path.sep).join('/');
    if (!rel || rel.startsWith('..') || path.isAbsolute(rel)) continue;
    // skip junk
    if (ent.name === '.DS_Store' || ent.name === 'Thumbs.db' || ent.name.startsWith('.')) continue;
    if (ent.isDirectory()) {
      await walkPackFiles(rootDir, full, out, totals);
      continue;
    }
    if (!ent.isFile()) continue;
    const st = await fsp.stat(full);
    if (st.size > PACK_READ_MAX_FILE_BYTES) {
      throw new Error(`File too large: ${rel} (${st.size} bytes)`);
    }
    if (totals.bytes + st.size > PACK_READ_MAX_TOTAL_BYTES) {
      throw new Error(`Pack total size exceeds ${PACK_READ_MAX_TOTAL_BYTES} bytes`);
    }
    const buf = await fsp.readFile(full);
    totals.bytes += buf.byteLength;
    out.push({ path: rel, base64: buf.toString('base64') });
  }
}

/** Read an entire resource pack directory into base64 file entries (for import). */
ipcMain.handle('fs:readPack', async (_, payload: { dir: string }) => {
  try {
    if (!payload || typeof payload.dir !== 'string') {
      throw new Error('Invalid payload for readPack');
    }
    const rootDir = path.resolve(payload.dir);
    const st = await fsp.stat(rootDir);
    if (!st.isDirectory()) {
      throw new Error('Not a directory');
    }
    const files: { path: string; base64: string }[] = [];
    const totals = { bytes: 0 };
    await walkPackFiles(rootDir, rootDir, files, totals);
    return { ok: true, root: rootDir, files };
  } catch (err: any) {
    return { ok: false, error: err?.message || String(err) };
  }
});

/** Read a binary file after open dialog (zip import). */
ipcMain.handle('fs:readBinaryFile', async (_, filePath: string) => {
  try {
    if (!filePath || typeof filePath !== 'string') {
      return { ok: false, error: 'Invalid path' };
    }
    const resolved = path.resolve(filePath);
    const st = await fsp.stat(resolved);
    if (!st.isFile()) return { ok: false, error: 'Not a file' };
    if (st.size > PACK_READ_MAX_TOTAL_BYTES) {
      return { ok: false, error: `File too large (${st.size} bytes)` };
    }
    const buf = await fsp.readFile(resolved);
    return { ok: true, path: resolved, base64: buf.toString('base64'), size: buf.byteLength };
  } catch (err: any) {
    return { ok: false, error: err?.message || String(err) };
  }
});
