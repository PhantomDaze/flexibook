import { useEffect, useState } from 'react';
import { useT } from './UiI18n';
import type { PackParts } from '../shared/packExport';

export interface PackExportFormValues {
  namespace: string;
  themeId: string;
  bookId: string;
  includeBook: boolean;
  packFormat: number;
  /** When set, only these pack sections are written */
  parts?: PackParts;
}

export type PackExportMode = 'full' | 'theme' | 'content' | 'lang' | 'fonts' | 'textures';

const MODE_META: Record<
  PackExportMode,
  { titleKey: string; hintKey: string; parts: PackParts; showThemeId?: boolean; showBookId?: boolean }
> = {
  full: {
    titleKey: 'pack.mode.full.title',
    hintKey: 'pack.mode.full.hint',
    parts: { meta: true, theme: true, textures: true, content: true, lang: true, fonts: true },
    showThemeId: true,
    showBookId: true,
  },
  theme: {
    titleKey: 'pack.mode.theme.title',
    hintKey: 'pack.mode.theme.hint',
    parts: { meta: true, theme: true, textures: false, content: false, lang: false, fonts: false },
    showThemeId: true,
  },
  textures: {
    titleKey: 'pack.mode.textures.title',
    hintKey: 'pack.mode.textures.hint',
    parts: { meta: true, theme: false, textures: true, content: false, lang: false, fonts: false },
  },
  content: {
    titleKey: 'pack.mode.content.title',
    hintKey: 'pack.mode.content.hint',
    parts: { meta: true, theme: false, textures: false, content: true, lang: false, fonts: false },
    showThemeId: true,
    showBookId: true,
  },
  lang: {
    titleKey: 'pack.mode.lang.title',
    hintKey: 'pack.mode.lang.hint',
    parts: { meta: true, theme: false, textures: false, content: false, lang: true, fonts: false },
  },
  fonts: {
    titleKey: 'pack.mode.fonts.title',
    hintKey: 'pack.mode.fonts.hint',
    parts: { meta: true, theme: false, textures: false, content: false, lang: false, fonts: true },
  },
};

export interface PackExportFormProps {
  onExport: (opts: PackExportFormValues) => void;
  mode?: PackExportMode;
  initial?: Partial<PackExportFormValues>;
  /** Override primary button label */
  buttonLabel?: string;
}

/** Shared pack export fields + primary action. */
export function PackExportForm({
  onExport,
  mode = 'full',
  initial,
  buttonLabel,
}: PackExportFormProps) {
  const t = useT();
  const meta = MODE_META[mode];
  const [packNamespace, setPackNamespace] = useState(initial?.namespace ?? 'myguide');
  const [packThemeId, setPackThemeId] = useState(initial?.themeId ?? 'main');
  const [packBookId, setPackBookId] = useState(initial?.bookId ?? 'guide');
  const [packFormat, setPackFormat] = useState(initial?.packFormat ?? 34);

  function handleExport() {
    const ns = (packNamespace || '').trim();
    if (!ns) {
      alert('namespace 必填');
      return;
    }
    if (!/^[a-z0-9_.-]+$/.test(ns)) {
      alert('namespace 格式不合法，应为 [a-z0-9_.-]+');
      return;
    }
    onExport({
      namespace: ns,
      themeId: (packThemeId || 'main').trim() || 'main',
      bookId: (packBookId || 'guide').trim() || 'guide',
      includeBook: !!meta.parts.content,
      packFormat: Number.isFinite(packFormat) && packFormat > 0 ? packFormat : 34,
      parts: meta.parts,
    });
  }

  const nsOk = !!packNamespace && /^[a-z0-9_.-]+$/.test((packNamespace || '').trim());

  return (
    <div className="pack-export-form">
      <p className="section-hint" style={{ marginTop: 0 }}>
        {t(meta.hintKey)}
      </p>
      <div className="field-grid">
        <div className="field">
          <label htmlFor={`pack-ns-${mode}`}>
            namespace <span style={{ color: 'var(--danger)' }}>*</span>
          </label>
          <input
            id={`pack-ns-${mode}`}
            type="text"
            className="mono"
            value={packNamespace}
            onChange={(e) => setPackNamespace(e.target.value)}
            placeholder="myguide"
            spellCheck={false}
          />
        </div>
        {meta.showThemeId && (
          <div className="field">
            <label htmlFor={`pack-theme-${mode}`}>{t('pack.themeId')}</label>
            <input
              id={`pack-theme-${mode}`}
              type="text"
              className="mono"
              value={packThemeId}
              onChange={(e) => setPackThemeId(e.target.value)}
              placeholder="main"
              spellCheck={false}
            />
          </div>
        )}
        {meta.showBookId && (
          <div className="field">
            <label htmlFor={`pack-book-${mode}`}>{t('pack.bookId')}</label>
            <input
              id={`pack-book-${mode}`}
              type="text"
              className="mono"
              value={packBookId}
              onChange={(e) => setPackBookId(e.target.value)}
              placeholder="guide"
              spellCheck={false}
            />
          </div>
        )}
        <div className="field">
          <label htmlFor={`pack-fmt-${mode}`}>{t('pack.packFormat')}</label>
          <input
            id={`pack-fmt-${mode}`}
            type="number"
            value={packFormat}
            onChange={(e) => setPackFormat(parseInt(e.target.value, 10) || 34)}
            min={1}
          />
        </div>
      </div>

      <div className="btn-row" style={{ marginTop: 10 }}>
        <button
          type="button"
          className="primary"
          onClick={handleExport}
          disabled={!nsOk}
          title={t(meta.hintKey)}
        >
          {buttonLabel || `${t(meta.titleKey)}…`}
        </button>
      </div>
    </div>
  );
}

export function PackExportModal({
  open,
  onClose,
  onExport,
  mode = 'full',
  initial,
}: {
  open: boolean;
  onClose: () => void;
  onExport: (opts: PackExportFormValues) => void;
  mode?: PackExportMode;
  initial?: Partial<PackExportFormValues>;
}) {
  const t = useT();
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  const meta = MODE_META[mode];

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal-panel" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <strong>{t(meta.titleKey)}</strong>
            <div className="muted" style={{ fontSize: 11, marginTop: 2 }}>
              {t(meta.hintKey)}
            </div>
          </div>
          <button type="button" onClick={onClose}>
            {t('pack.close')}
          </button>
        </div>
        {/* remount form when mode/open changes so initial ns/ids apply */}
        <PackExportForm
          key={`${mode}-${initial?.namespace || ''}-${initial?.themeId || ''}-${initial?.bookId || ''}`}
          mode={mode}
          initial={initial}
          onExport={(opts) => {
            onExport(opts);
            onClose();
          }}
        />
      </div>
    </div>
  );
}

/** Re-export mode parts helper for App */
export function partsForMode(mode: PackExportMode): PackParts {
  return MODE_META[mode].parts;
}
