import { useRef } from 'react';
import type { AdaptiveBookContent } from '../shared/types';
import type { CustomFont } from '../shared/customFonts';
import {
  DEFAULT_TTF_OVERSAMPLE,
  DEFAULT_TTF_SIZE,
  isValidFontId,
} from '../shared/customFonts';
import { FLEXIBOOK_DEFAULT_FONT } from '../shared/UnihexFont';

export interface FontPanelProps {
  fonts: CustomFont[];
  onChange: (next: CustomFont[]) => void;
  /** Parent loads bytes → BrowserFont and appends to list */
  onImportFiles: (files: FileList) => void | Promise<void>;
  content: AdaptiveBookContent;
  onContentChange: (next: AdaptiveBookContent) => void;
  busy?: boolean;
  /** Partial pack: imported TTF/OTF + font json only */
  onExportFontsPack?: () => void;
}

export function FontPanel({
  fonts,
  onChange,
  onImportFiles,
  content,
  onContentChange,
  busy = false,
  onExportFontsPack,
}: FontPanelProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);

  function updateFont(id: string, patch: Partial<CustomFont>) {
    onChange(fonts.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  }

  function removeFont(id: string) {
    onChange(fonts.filter((x) => x.id !== id));
    if (content.defaultFont === id) {
      const c = { ...content };
      delete c.defaultFont;
      onContentChange(c);
    }
  }

  function setAsBookDefault(id: string) {
    onContentChange({ ...content, defaultFont: id });
  }

  function renameId(oldId: string, newId: string) {
    const id = newId.trim();
    if (!isValidFontId(id)) {
      alert('字体 id 需为 namespace:path，且符合 [a-z0-9_./-]+');
      return;
    }
    if (fonts.some((f) => f.id === id && f.id !== oldId)) {
      alert('id 已存在');
      return;
    }
    onChange(fonts.map((f) => (f.id === oldId ? { ...f, id } : f)));
    if (content.defaultFont === oldId) {
      onContentChange({ ...content, defaultFont: id });
    }
  }

  return (
    <div>
      <div className="section">
        <div className="section-head">
          <h4 className="section-title">自定义字体</h4>
          <span className="hint">{fonts.length} 个 · TTF/OTF</span>
        </div>
        <p className="section-hint">
          导入后可作书级 <span className="mono">defaultFont</span> 或 span/heading 的{' '}
          <span className="mono">font</span>。预览使用浏览器 FontFace（
          <strong>近似</strong>，非游戏逐像素 parity）。导出写入{' '}
          <span className="mono">assets/&lt;ns&gt;/font/*.json</span> + ttf/otf。默认书字体仍为{' '}
          <span className="mono">{FLEXIBOOK_DEFAULT_FONT}</span>。
        </p>
        <div className="toolbar">
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            导入 TTF/OTF…
          </button>
          {onExportFontsPack && (
            <button
              type="button"
              className="primary"
              disabled={fonts.length === 0}
              onClick={onExportFontsPack}
              title="仅 font/*.json + ttf/otf"
            >
              导出字体资源包…
            </button>
          )}
          <input
            ref={inputRef}
            type="file"
            accept=".ttf,.otf,font/ttf,font/otf"
            multiple
            hidden
            onChange={(e) => {
              const fl = e.target.files;
              e.target.value = '';
              if (fl) void onImportFiles(fl);
            }}
          />
        </div>
      </div>

      <div className="section">
        {fonts.length === 0 && (
          <div className="empty-state">尚未导入自定义字体。unihex 默认始终可用。</div>
        )}
        <div className="element-list">
          {fonts.map((f) => (
            <div key={f.family} className="element-card">
              <div className="element-card-head">
                <span className="element-type">
                  font
                  <span
                    className={`badge ${
                      f.status === 'ready' ? 'ok-badge' : f.status === 'error' ? 'warn-badge' : ''
                    }`}
                  >
                    {f.status}
                  </span>
                  {content.defaultFont === f.id && <span className="badge">书默认</span>}
                </span>
                <div className="element-actions">
                  <button type="button" className="danger" title="删除" onClick={() => removeFont(f.id)}>
                    ✕
                  </button>
                </div>
              </div>
              <div className="element-body">
                <div className="field full">
                  <label>id</label>
                  <input
                    type="text"
                    className="mono"
                    defaultValue={f.id}
                    key={f.id}
                    onBlur={(e) => {
                      if (e.target.value.trim() !== f.id) renameId(f.id, e.target.value);
                    }}
                  />
                </div>
                <div className="hint mono">{f.fileName}</div>
                {f.error && <div className="banner banner-warn">{f.error}</div>}
                <div className="field-grid">
                  <div className="field">
                    <label>size</label>
                    <input
                      type="number"
                      value={f.size}
                      step={0.5}
                      onChange={(e) =>
                        updateFont(f.id, { size: parseFloat(e.target.value) || DEFAULT_TTF_SIZE })
                      }
                    />
                  </div>
                  <div className="field">
                    <label>oversample</label>
                    <input
                      type="number"
                      value={f.oversample}
                      step={0.5}
                      onChange={(e) =>
                        updateFont(f.id, {
                          oversample: parseFloat(e.target.value) || DEFAULT_TTF_OVERSAMPLE,
                        })
                      }
                    />
                  </div>
                </div>
                <div className="btn-row">
                  <button
                    type="button"
                    disabled={f.status !== 'ready'}
                    onClick={() => setAsBookDefault(f.id)}
                  >
                    设为书默认字体
                  </button>
                  {content.defaultFont === f.id && (
                    <button
                      type="button"
                      className="ghost"
                      onClick={() => {
                        const c = { ...content };
                        delete c.defaultFont;
                        onContentChange(c);
                      }}
                    >
                      清除书默认
                    </button>
                  )}
                </div>
                {f.status === 'ready' && (
                  <div
                    className="font-preview-sample"
                    style={{ fontFamily: `"${f.family}"`, fontSize: 16 }}
                  >
                    Sample 预览 Aa 中文 0123
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">当前书 defaultFont</h4>
        </div>
        <input
          type="text"
          className="mono"
          list="font-id-list-global"
          value={content.defaultFont || ''}
          placeholder={FLEXIBOOK_DEFAULT_FONT}
          onChange={(e) => {
            const v = e.target.value.trim();
            const c = { ...content };
            if (v) c.defaultFont = v;
            else delete c.defaultFont;
            onContentChange(c);
          }}
        />
        <datalist id="font-id-list-global">
          <option value={FLEXIBOOK_DEFAULT_FONT} />
          {fonts.map((f) => (
            <option key={f.id} value={f.id} />
          ))}
        </datalist>
      </div>
    </div>
  );
}
