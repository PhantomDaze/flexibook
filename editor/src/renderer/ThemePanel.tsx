import { useRef } from 'react';
import { useT } from './UiI18n';
import type { BookTheme } from '../shared/types';
import type { CustomTexture, CustomTextures, TextureSlot } from './customTextures';
import { DEFAULT_ITEM_TEXTURE, loadImageFile, resolveThemeAssetUrl } from './customTextures';
import { DEFAULT_THEME } from './defaults';
export interface ThemePanelProps {
  theme: BookTheme;
  onChange: (next: BookTheme) => void;
  customTextures: CustomTextures;
  onCustomTexture: (slot: TextureSlot, tex: CustomTexture | null, opts?: { syncSize?: boolean }) => void;
  onResetDefault: () => void;
  onLoadContain: () => void;
  onExport: () => void;
  onLoad?: () => void;
  onSave?: () => void;
  /** Partial pack: theme JSON only */
  onExportThemePack?: () => void;
  /** Partial pack: book texture only */
  onExportTexturesPack?: () => void;
}

const LAYOUT_KEYS: (keyof BookTheme)[] = [
  'pageContentWidth',
  'pageContentHeight',
  'lineHeight',
  'paragraphGap',
  'headingGap',
  'gutter',
  'bulletIndent',
  'dividerHeight',
  'contentLeft',
  'contentTop',
  'titleOffsetY',
  'contentOffsetY',
  'pageLabelInsetY',
  'bookTexWidth',
  'bookTexHeight',
];

function toHex(value: number): string {
  return '#' + (value & 0xffffff).toString(16).padStart(6, '0');
}

export function ThemePanel({
  theme,
  onChange,
  customTextures,
  onCustomTexture,
  onResetDefault,
  onLoadContain,
  onExport,
  onLoad,
  onSave,
  onExportThemePack,
  onExportTexturesPack,
}: ThemePanelProps) {
  const t = useT();
  function set<K extends keyof BookTheme>(key: K, value: BookTheme[K]) {
    const next = { ...theme, [key]: value } as BookTheme;
    if (LAYOUT_KEYS.includes(key)) {
      next.revision = (theme.revision || 1) + 1;
    }
    onChange(next);
  }

  function setColor(
    key: 'pageTextColor' | 'linkColor' | 'highlightColor' | 'dividerColor',
    hex: string,
  ) {
    const cleaned = hex.trim().replace(/^#/, '');
    if (!/^[0-9a-fA-F]{6}$/.test(cleaned)) return;
    set(key, parseInt(cleaned, 16));
  }

  return (
    <div>
      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.textures')}</h4>
          <span className="hint">{t('theme.section.texturesHint')}</span>
        </div>

        <TexturePicker
          title={t('theme.bookBg')}
          path={theme.bookTexture}
          defaultPath={DEFAULT_THEME.bookTexture}
          custom={customTextures.book}
          onPathChange={(p) => set('bookTexture', p)}
          onPickFile={async (file) => {
            const tex = await loadImageFile(file);
            onCustomTexture('book', tex, { syncSize: true });
          }}
          onClearCustom={() => onCustomTexture('book', null)}
          onResetPath={() => {
            onCustomTexture('book', null);
            set('bookTexture', DEFAULT_THEME.bookTexture);
            set('bookTexWidth', DEFAULT_THEME.bookTexWidth);
            set('bookTexHeight', DEFAULT_THEME.bookTexHeight);
          }}
        />

        <div style={{ marginTop: 10 }}>
          <TexturePicker
            title={t('theme.itemIcon')}
            path={DEFAULT_ITEM_TEXTURE}
            defaultPath={DEFAULT_ITEM_TEXTURE}
            custom={customTextures.item}
            onPathChange={() => {
              /* fixed game path: flexibook:item/flexi_book */
            }}
            onPickFile={async (file) => {
              const tex = await loadImageFile(file);
              onCustomTexture('item', tex);
            }}
            onClearCustom={() => onCustomTexture('item', null)}
            onResetPath={() => onCustomTexture('item', null)}
            pathLocked
          />
        </div>

        <div className="field-grid" style={{ marginTop: 10 }}>
          <NumField
            label="bookTexWidth"
            value={theme.bookTexWidth}
            onChange={(v) => set('bookTexWidth', Math.max(1, v))}
          />
          <NumField
            label="bookTexHeight"
            value={theme.bookTexHeight}
            onChange={(v) => set('bookTexHeight', Math.max(1, v))}
          />
          <NumField
            label="textureSheetSize"
            value={theme.textureSheetSize}
            onChange={(v) => set('textureSheetSize', Math.max(1, v))}
          />
        </div>
        <p className="section-hint">
          {t('theme.texturesHelp')}
          <br />
          {t('theme.itemHelp')}
        </p>
        {onExportTexturesPack && (
          <div className="toolbar" style={{ marginTop: 8 }}>
            <button
              type="button"
              className="primary"
              onClick={onExportTexturesPack}
              title={t('theme.exportTexturesTitle')}
            >
              {t('theme.exportTextures')}
            </button>
          </div>
        )}
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.layout')}</h4>
          <span className="hint">{t('theme.section.layoutHint')}</span>
        </div>
        <div className="field-grid">
          <NumField label="pageContentWidth" value={theme.pageContentWidth} onChange={(v) => set('pageContentWidth', v)} />
          <NumField label="pageContentHeight" value={theme.pageContentHeight} onChange={(v) => set('pageContentHeight', v)} />
          <NumField label="lineHeight" value={theme.lineHeight} onChange={(v) => set('lineHeight', v)} />
          <NumField label="paragraphGap" value={theme.paragraphGap} onChange={(v) => set('paragraphGap', v)} />
          <NumField label="headingGap" value={theme.headingGap} onChange={(v) => set('headingGap', v)} />
          <NumField label="gutter" value={theme.gutter} onChange={(v) => set('gutter', v)} />
          <NumField label="bulletIndent" value={theme.bulletIndent} onChange={(v) => set('bulletIndent', v)} />
          <NumField label="dividerHeight" value={theme.dividerHeight} onChange={(v) => set('dividerHeight', v)} />
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.offsets')}</h4>
        </div>
        <div className="field-grid">
          <NumField label="contentLeft" value={theme.contentLeft} onChange={(v) => set('contentLeft', v)} />
          <NumField label="contentTop" value={theme.contentTop} onChange={(v) => set('contentTop', v)} />
          <NumField label="titleOffsetY" value={theme.titleOffsetY} onChange={(v) => set('titleOffsetY', v)} />
          <NumField label="contentOffsetY" value={theme.contentOffsetY} onChange={(v) => set('contentOffsetY', v)} />
          <NumField label="pageLabelInsetY" value={theme.pageLabelInsetY} onChange={(v) => set('pageLabelInsetY', v)} />
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.colors')}</h4>
          <span className="hint">{t('theme.section.colorsHint')}</span>
        </div>
        <div className="field-grid">
          <ColorField label="pageText" value={theme.pageTextColor} onChange={(hex) => setColor('pageTextColor', hex)} />
          <ColorField label="link" value={theme.linkColor} onChange={(hex) => setColor('linkColor', hex)} />
          <ColorField label="highlight" value={theme.highlightColor} onChange={(hex) => setColor('highlightColor', hex)} />
          <ColorField label="divider" value={theme.dividerColor} onChange={(hex) => setColor('dividerColor', hex)} />
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.imageFit')}</h4>
        </div>
        <div className="field">
          <label htmlFor="imageFit">imageFit</label>
          <select
            id="imageFit"
            value={theme.imageFit}
            onChange={(e) => set('imageFit', e.target.value as 'stretch' | 'contain')}
          >
            <option value="stretch">{t('theme.imageFit.stretch')}</option>
            <option value="contain">{t('theme.imageFit.contain')}</option>
          </select>
        </div>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('theme.section.export')}</h4>
          <span className="hint">{t('theme.section.exportHint')}</span>
        </div>
        <p className="section-hint">
          {t('theme.exportHelp')}
        </p>
        <div className="toolbar" style={{ marginTop: 8 }}>
          {onExportThemePack && (
            <button
              type="button"
              className="primary"
              onClick={onExportThemePack}
              title={t('theme.exportThemePackTitle')}
            >
              {t('theme.exportThemePack')}
            </button>
          )}
          <button type="button" onClick={onExport} title={t('theme.exportJsonTitle')}>
            {t('theme.exportJson')}
          </button>
        </div>
      </div>

      <div className="toolbar sticky-actions">
        {onLoad && (
          <button type="button" onClick={onLoad} title={t('theme.openTitle')}>
            {t('theme.open')}
          </button>
        )}
        {onSave && (
          <button type="button" onClick={onSave} title={t('theme.saveTitle')}>
            {t('theme.save')}
          </button>
        )}
        <button type="button" onClick={onResetDefault}>
          {t('theme.resetDefault')}
        </button>
        <button type="button" onClick={onLoadContain}>
          {t('theme.loadContain')}
        </button>
      </div>
    </div>
  );
}

function TexturePicker({
  // i18n

  title,
  path,
  defaultPath,
  custom,
  onPathChange,
  onPickFile,
  onClearCustom,
  onResetPath,
  pathLocked = false,
}: {
  title: string;
  path: string;
  defaultPath: string;
  custom: CustomTexture | null;
  onPathChange: (path: string) => void;
  onPickFile: (file: File) => void | Promise<void>;
  onClearCustom: () => void;
  onResetPath: () => void;
  /** When true, resource path is display-only (e.g. fixed item icon). */
  pathLocked?: boolean;
}) {
  const t = useT();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const previewSrc = custom?.url || resolveThemeAssetUrl(path);
  const isCustom = !!custom;
  const isDefault = !isCustom && path === defaultPath;

  return (
    <div className="texture-card">
      <div className="texture-card-head">
        <div className="texture-title">{title}</div>
        <span className={`texture-badge ${isCustom ? 'custom' : isDefault ? 'default' : 'path'}`}>
          {isCustom ? t('theme.badge.local') : isDefault ? t('theme.badge.default') : t('theme.badge.path')}
        </span>
      </div>

      <div className="texture-row">
        <div className="texture-thumb" title={custom?.fileName || path}>
          {previewSrc ? (
            <img src={previewSrc} alt="" draggable={false} />
          ) : (
            <span className="muted">{t('theme.none')}</span>
          )}
        </div>
        <div className="texture-meta">
          <input
            type="text"
            className="mono"
            value={isCustom ? custom!.fileName : path}
            disabled={isCustom || pathLocked}
            spellCheck={false}
            onChange={(e) => onPathChange(e.target.value.trim())}
            placeholder="namespace:textures/gui/book.png"
            title={
              pathLocked
                ? t('theme.pathLocked')
                : isCustom
                  ? t('theme.pathCustomLocked')
                  : t('theme.pathEditable')
            }
          />
          {isCustom && (
            <div className="hint">
              {custom!.naturalWidth}×{custom!.naturalHeight}px · {custom!.fileName}
            </div>
          )}
          <div className="btn-row" style={{ marginTop: 6 }}>
            <button type="button" onClick={() => inputRef.current?.click()}>
              {t('theme.pickImage')}
            </button>
            {isCustom && (
              <button type="button" onClick={onClearCustom}>
                {t('theme.clearLocal')}
              </button>
            )}
            <button type="button" className="ghost" onClick={onResetPath} disabled={isDefault && !isCustom}>
              {t('theme.resetPath')}
            </button>
          </div>
          <input
            ref={inputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp,image/*"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0];
              e.target.value = '';
              if (f) void onPickFile(f);
            }}
          />
        </div>
      </div>
    </div>
  );
}

function NumField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="field">
      <label htmlFor={label}>{label}</label>
      <input
        id={label}
        type="number"
        value={Number.isFinite(value) ? value : 0}
        onChange={(e) => {
          const n = parseInt(e.target.value, 10);
          onChange(Number.isFinite(n) ? n : 0);
        }}
      />
    </div>
  );
}

function ColorField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (hex: string) => void;
}) {
  const hex = toHex(value);
  const id = `color-${label}`;
  return (
    <div className="color-field">
      <label htmlFor={id}>{label}</label>
      <input
        type="color"
        value={hex}
        onChange={(e) => onChange(e.target.value)}
        title={hex}
        aria-label={`${label} picker`}
      />
      <input
        id={id}
        type="text"
        value={hex}
        spellCheck={false}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
