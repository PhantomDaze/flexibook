import { contextBridge, ipcRenderer } from 'electron';

export interface ElectronAPI {
  openFile: (options?: any) => Promise<any>;
  saveFile: (options?: any) => Promise<any>;
  showMessage: (options: any) => Promise<any>;
  openDirectory: (options?: any) => Promise<any>;
  writePack: (payload: {
    dir: string;
    files: { path: string; base64: string }[];
  }) => Promise<{ ok: boolean; root?: string; error?: string }>;
  readPack: (payload: {
    dir: string;
  }) => Promise<{
    ok: boolean;
    root?: string;
    files?: { path: string; base64: string }[];
    error?: string;
  }>;
  readBinaryFile: (
    filePath: string,
  ) => Promise<{ ok: boolean; path?: string; base64?: string; size?: number; error?: string }>;
  readTextFile: (filePath: string) => Promise<{ ok: boolean; path?: string; text?: string; error?: string }>;
  writeTextFile: (payload: {
    path: string;
    text: string;
  }) => Promise<{ ok: boolean; path?: string; error?: string }>;
}

const api: ElectronAPI = {
  openFile: (options) => ipcRenderer.invoke('dialog:openFile', options),
  saveFile: (options) => ipcRenderer.invoke('dialog:saveFile', options),
  showMessage: (options) => ipcRenderer.invoke('dialog:message', options),
  openDirectory: (options) => ipcRenderer.invoke('dialog:openDirectory', options),
  writePack: (payload) => ipcRenderer.invoke('fs:writePack', payload),
  readPack: (payload) => ipcRenderer.invoke('fs:readPack', payload),
  readBinaryFile: (filePath) => ipcRenderer.invoke('fs:readBinaryFile', filePath),
  readTextFile: (filePath) => ipcRenderer.invoke('fs:readTextFile', filePath),
  writeTextFile: (payload) => ipcRenderer.invoke('fs:writeTextFile', payload),
};

contextBridge.exposeInMainWorld('electronAPI', api);
