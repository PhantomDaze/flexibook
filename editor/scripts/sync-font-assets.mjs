#!/usr/bin/env node
/**
 * Sync font assets for editor from the mod resource tree.
 * - Copies flexibook/font/* (zip + json + license) into both
 *   editor/public/assets/flexibook/font/ (served at /assets/...) and
 *   editor/assets/flexibook/font/ (for Vite import in some setups).
 * - If source dir missing (zip not yet packed), warn + exit 0
 *   so `npm install` / dev bootstrap does not break.
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SRC_DIR = path.resolve(__dirname, '../../src/main/resources/assets/flexibook/font');
const DST_PUBLIC = path.resolve(__dirname, '../public/assets/flexibook/font');
const DST_ASSETS = path.resolve(__dirname, '../assets/flexibook/font');

function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}

function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function copyAll(src, dst) {
  ensureDir(dst);
  fs.cpSync(src, dst, { recursive: true, force: true });
  return fs.readdirSync(dst);
}

function main() {
  if (!fs.existsSync(SRC_DIR)) {
    console.warn(
      '[sync-font-assets] Source font directory missing:\n' +
        `  ${SRC_DIR}\n` +
        '  Continuing without error so install/dev can proceed.',
    );
    process.exit(0);
  }

  const zipName = 'unifont_all-17.0.05.zip';
  if (!fs.existsSync(path.join(SRC_DIR, zipName))) {
    console.warn(`[sync-font-assets] ${zipName} missing under ${SRC_DIR}; copying whatever is present.`);
  }

  console.log('[sync-font-assets] Copying font assets from mod resources...');
  const copiedPublic = copyAll(SRC_DIR, DST_PUBLIC);
  const copiedAssets = copyAll(SRC_DIR, DST_ASSETS);

  const zipPublic = path.join(DST_PUBLIC, zipName);
  if (fs.existsSync(zipPublic)) {
    const h = sha256(fs.readFileSync(zipPublic));
    console.log(`[sync-font-assets] Synced ${zipName} (sha256=${h}) to public/assets and assets/`);
  } else {
    console.log('[sync-font-assets] No zip found after copy.');
  }

  console.log('[sync-font-assets] Files now in public:', copiedPublic.join(', '));
  console.log('[sync-font-assets] Files now in assets :', copiedAssets.join(', '));
}

main();
