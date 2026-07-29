#!/usr/bin/env node
// Simple one-command dev for the editor:
// - starts Vite dev server for the renderer (HMR)
// - compiles main/preload to dist-electron (CommonJS for Electron)
// - launches Electron with VITE_DEV_SERVER_URL so renderer loads from Vite
// - restarts Electron on main changes is out of scope for this minimal helper

const { spawn } = require('child_process');
const { join } = require('path');

const root = __dirname.replace(/scripts$/, '');

function run(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: 'inherit', shell: true, ...opts });
    p.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(cmd + ' exited ' + code))));
  });
}

async function main() {
  // Compile main process once for this dev session
  await run('npm', ['run', 'build:main'], { cwd: root });

  // Start Vite dev server (non-blocking)
  const vite = spawn('npm', ['run', 'dev', '--', '--host', '127.0.0.1'], {
    cwd: root,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: true,
    env: { ...process.env, FORCE_COLOR: '1' },
  });

  // Wait until Vite prints the local URL
  let url = null;
  await new Promise((resolve) => {
    const onData = (buf) => {
      const s = buf.toString();
      const m = s.match(/http:\/\/localhost:\d+/);
      if (m) {
        url = m[0];
        vite.stdout.off('data', onData);
        vite.stderr.off('data', onData);
        resolve();
      }
    };
    vite.stdout.on('data', onData);
    vite.stderr.on('data', onData);
  });

  console.log('[dev-electron] Vite at', url);

  // Launch Electron
  const electronBin = require('electron');
  const electronProc = spawn(electronBin, ['.'], {
    cwd: root,
    stdio: 'inherit',
    env: { ...process.env, VITE_DEV_SERVER_URL: url },
  });

  const cleanup = () => {
    try { vite.kill('SIGINT'); } catch {}
    try { electronProc.kill('SIGINT'); } catch {}
  };
  process.on('SIGINT', cleanup);
  process.on('SIGTERM', cleanup);

  electronProc.on('exit', () => cleanup());
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
