import { useRef } from 'react';
import { useT } from './UiI18n';
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
  const t = useT();
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
      alert(t('font.badId'));
      return;
    }
    if (fonts.some((f) => f.id === id && f.id !== oldId)) {
      alert(t('font.idExists'));
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
          <h4 className="section-title">{t('font.section.custom')}</h4>
          <span className="hint">{t('font.count', { n: fonts.length })}</span>
        </div>
        <p className="section-hint">{t('font.help')}</p>
        <div className="toolbar">
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            {t('font.import')}
          </button>
          {onExportFontsPack && (
            <button
              type="button"
              className="primary"
              disabled={fonts.length === 0}
              onClick={onExportFontsPack}
              title={t('font.exportPackTitle')}
            >
              {t('font.exportPack')}
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
          <div className="empty-state">{t('font.empty')}</div>
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
                  {content.defaultFont === f.id && <span className="badge">{t('font.bookDefaultBadge')}</span>}
                </span>
                <div className="element-actions">
                  <button type="button" className="danger" title={t('font.delete')} onClick={() => removeFont(f.id)}>
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
                    {t('font.setBookDefault')}
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
                      {t('font.clearBookDefault')}
                    </button>
                  )}
                </div>
                {f.status === 'ready' && (
                  <div
                    className="font-preview-sample"
                    style={{ fontFamily: `"${f.family}"`, fontSize: 16 }}
                  >
                    {t('font.sample')}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('font.section.bookDefault')}</h4>
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
