import { contextBridge, ipcRenderer } from 'electron';

export interface ElectronAPI {
  openFile: (options?: any) => Promise<any>;
  saveFile: (options?: any) => Promise<any>;
  showMessage: (options: any) => Promise<any>;
  openDirectory: (options?: any) => Promise<any>;
  writePack: (payload: { dir: string; files: { path: string; base64: string }[] }) => Promise<{ ok: boolean; root?: string; error?: string }>;
}

const api: ElectronAPI = {
  openFile: (options) => ipcRenderer.invoke('dialog:openFile', options),
  saveFile: (options) => ipcRenderer.invoke('dialog:saveFile', options),
  showMessage: (options) => ipcRenderer.invoke('dialog:message', options),
  openDirectory: (options) => ipcRenderer.invoke('dialog:openDirectory', options),
  writePack: (payload) => ipcRenderer.invoke('fs:writePack', payload),
};

contextBridge.exposeInMainWorld('electronAPI', api);
