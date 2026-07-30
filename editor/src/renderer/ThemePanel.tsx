import { useRef } from 'react';
import type { BookTheme } from '../shared/types';
import type { CustomTexture, CustomTextures, TextureSlot } from './customTextures';
import { loadImageFile, resolveThemeAssetUrl } from './customTextures';
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
          <h4 className="section-title">纹理 / 背景</h4>
          <span className="hint">本地文件仅预览用</span>
        </div>

        <TexturePicker
          title="书背景 book"
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
          选择本地 book.png 后立即在预览中使用。游戏与编辑器一致：贴图按固定 2048×2048 图集，
          整张映射到书页面板（bookTexWidth×bookTexHeight，默认 192×216）。
          请提供 2048×2048 的 book.png（或会被按整图取样）；布局尺寸改 bookTexWidth/Height。
          翻页使用预览区下方简单按钮（游戏内为原版 GUI 按钮）。
          「导出主题资源包」只含 theme JSON；「导出纹理资源包」只含 book.png。完整包请用顶栏按钮。
        </p>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">布局参数</h4>
          <span className="hint">改动会触发重排</span>
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
          <h4 className="section-title">偏移与标签</h4>
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
          <h4 className="section-title">颜色</h4>
          <span className="hint">仅重绘</span>
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
          <h4 className="section-title">图片适配</h4>
        </div>
        <div className="field">
          <label htmlFor="imageFit">imageFit</label>
          <select
            id="imageFit"
            value={theme.imageFit}
            onChange={(e) => set('imageFit', e.target.value as 'stretch' | 'contain')}
          >
            <option value="stretch">STRETCH — 填满（可能变形）</option>
            <option value="contain">CONTAIN — 保持比例居中</option>
          </select>
        </div>
      </div>

      <div className="toolbar sticky-actions">
        {onLoad && (
          <button type="button" onClick={onLoad} title="从磁盘加载主题 JSON">
            打开…
          </button>
        )}
        {onSave && (
          <button type="button" onClick={onSave} title="保存主题 JSON 到磁盘">
            保存…
          </button>
        )}
        <button type="button" onClick={onResetDefault}>
          重置默认
        </button>
        <button type="button" onClick={onLoadContain}>
          Contain 示例
        </button>
        <button type="button" onClick={onExport}>
          导出主题 JSON
        </button>
        {onExportThemePack && (
          <button type="button" className="primary" onClick={onExportThemePack} title="仅 themes/*.json">
            导出主题资源包…
          </button>
        )}
        {onExportTexturesPack && (
          <button type="button" className="primary" onClick={onExportTexturesPack} title="仅 textures/gui PNG">
            导出纹理资源包…
          </button>
        )}
      </div>
    </div>
  );
}

function TexturePicker({
  title,
  path,
  defaultPath,
  custom,
  onPathChange,
  onPickFile,
  onClearCustom,
  onResetPath,
}: {
  title: string;
  path: string;
  defaultPath: string;
  custom: CustomTexture | null;
  onPathChange: (path: string) => void;
  onPickFile: (file: File) => void | Promise<void>;
  onClearCustom: () => void;
  onResetPath: () => void;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const previewSrc = custom?.url || resolveThemeAssetUrl(path);
  const isCustom = !!custom;
  const isDefault = !isCustom && path === defaultPath;

  return (
    <div className="texture-card">
      <div className="texture-card-head">
        <div className="texture-title">{title}</div>
        <span className={`texture-badge ${isCustom ? 'custom' : isDefault ? 'default' : 'path'}`}>
          {isCustom ? '本地文件' : isDefault ? '默认' : '资源路径'}
        </span>
      </div>

      <div className="texture-row">
        <div className="texture-thumb" title={custom?.fileName || path}>
          {previewSrc ? (
            <img src={previewSrc} alt="" draggable={false} />
          ) : (
            <span className="muted">无</span>
          )}
        </div>
        <div className="texture-meta">
          <input
            type="text"
            className="mono"
            value={isCustom ? custom!.fileName : path}
            disabled={isCustom}
            spellCheck={false}
            onChange={(e) => onPathChange(e.target.value.trim())}
            placeholder="namespace:textures/gui/book.png"
            title={isCustom ? '使用本地文件时路径锁定；清除后可改资源路径' : '资源定位符，预览会从 assets 加载'}
          />
          {isCustom && (
            <div className="hint">
              {custom!.naturalWidth}×{custom!.naturalHeight}px · {custom!.fileName}
            </div>
          )}
          <div className="btn-row" style={{ marginTop: 6 }}>
            <button type="button" onClick={() => inputRef.current?.click()}>
              选择图片…
            </button>
            {isCustom && (
              <button type="button" onClick={onClearCustom}>
                清除本地
              </button>
            )}
            <button type="button" className="ghost" onClick={onResetPath} disabled={isDefault && !isCustom}>
              恢复默认
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
