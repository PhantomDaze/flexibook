import { useRef, useState } from 'react';
import { useT } from './UiI18n';
import type {
  AdaptiveBookContent,
  BookElement,
  InlineSpan,
  StyleFlags,
  TranslatableText,
} from '../shared/types';
import { StyleFlags as SF, LinkAction as LA } from '../shared/types';
import { FLEXIBOOK_DEFAULT_FONT } from '../shared/UnihexFont';
import { LangKeyPickerModal, type LangTables } from './LangPanel';

export interface ContentPanelProps {
  content: AdaptiveBookContent;
  onChange: (next: AdaptiveBookContent) => void;
  lang: string;
  onResetDemo?: () => void;
  onExport?: () => void;
  /** Partial pack: contents + books index only */
  onExportContentPack?: () => void;
  onLoad?: () => void;
  onSave?: () => void;
  /** Registered font ids for datalist (include flexibook:default) */
  fontIds?: string[];
  langTables?: LangTables;
  onEnsureLangKey?: (key: string) => void;
}

const ADD_BUTTONS: { type: BookElement['type']; labelKey: string }[] = [
  { type: 'paragraph', labelKey: 'content.el.paragraph' },
  { type: 'heading', labelKey: 'content.el.heading' },
  { type: 'bullet', labelKey: 'content.el.bullet' },
  { type: 'divider', labelKey: 'content.el.divider' },
  { type: 'br', labelKey: 'content.el.br' },
  { type: 'image', labelKey: 'content.el.image' },
  { type: 'box', labelKey: 'content.el.box' },
];

function typeLabel(el: BookElement): string {
  switch (el.type) {
    case 'heading':
      return 'heading';
    case 'paragraph':
      return 'paragraph';
    case 'bullet':
      return 'bullet';
    case 'divider':
      return 'divider';
    case 'br':
      return 'break';
    case 'image':
      return 'image';
    case 'box':
      return 'box';
    default:
      return (el as BookElement).type;
  }
}

function colorToHex(c?: number): string {
  if (c == null || !Number.isFinite(c)) return '';
  return '#' + (c & 0xffffff).toString(16).padStart(6, '0').toUpperCase();
}

function hexToColor(hex: string): number | undefined {
  const s = hex.trim().replace(/^#/, '');
  if (!/^[0-9a-fA-F]{6}$/.test(s)) return undefined;
  return parseInt(s, 16);
}

function emptySpan(): InlineSpan {
  return { text: '', translate: false, style: { ...SF.EMPTY } };
}

function ensureSpans(spans?: InlineSpan[]): InlineSpan[] {
  return spans && spans.length > 0 ? spans.map((s) => ({ ...s, style: { ...SF.EMPTY, ...s.style } })) : [emptySpan()];
}

function isExternalFont(id?: string | null): boolean {
  if (!id) return false;
  return id !== FLEXIBOOK_DEFAULT_FONT;
}

/* ---------- Span editor ---------- */

interface SpanEditorProps {
  spans: InlineSpan[];
  onChange: (next: InlineSpan[]) => void;
  fontIds: string[];
  onPickKey?: (apply: (key: string) => void) => void;
}

function SpanEditor({
  spans, onChange, fontIds, onPickKey }: SpanEditorProps) {
  const t = useT();
  const list = ensureSpans(spans);

  function update(i: number, patch: Partial<InlineSpan>) {
    const next = list.map((s, idx) => (idx === i ? { ...s, ...patch } : s));
    onChange(next);
  }

  function updateStyle(i: number, patch: Partial<StyleFlags>) {
    const s = list[i]!;
    update(i, { style: { ...s.style, ...patch } });
  }

  function toggleFlag(i: number, flag: 'bold' | 'italic' | 'underline') {
    const s = list[i]!;
    updateStyle(i, { [flag]: !s.style?.[flag] });
  }

  function setLink(i: number, kind: 'none' | 'command' | 'url', value: string) {
    if (kind === 'none' || !value.trim()) {
      const next = list.map((sp, idx) => {
        if (idx !== i) return sp;
        const copy = { ...sp };
        delete copy.link;
        return copy;
      });
      onChange(next);
      return;
    }
    if (kind === 'command') update(i, { link: LA.command(value.trim()) });
    else update(i, { link: LA.url(value.trim()) });
  }

  function addSpan() {
    onChange([...list, emptySpan()]);
  }

  function removeSpan(i: number) {
    if (list.length <= 1) {
      onChange([emptySpan()]);
      return;
    }
    onChange(list.filter((_, idx) => idx !== i));
  }

  function moveSpan(i: number, dir: -1 | 1) {
    const j = i + dir;
    if (j < 0 || j >= list.length) return;
    const next = [...list];
    const tmp = next[i]!;
    next[i] = next[j]!;
    next[j] = tmp;
    onChange(next);
  }

  return (
    <div className="span-list">
      {list.map((span, i) => {
        const link = span.link;
        const linkKind = !link || link.type === 'none' ? 'none' : link.type;
        const linkValue =
          linkKind === 'command' ? (link as { id: string }).id : linkKind === 'url' ? (link as { url: string }).url : '';
        const colorHex = colorToHex(span.style?.color);
        const fontExt = isExternalFont(span.style?.font);

        return (
          <div key={i} className="span-card">
            <div className="span-card-head">
              <span className="badge">span #{i + 1}</span>
              <div className="element-actions">
                <button type="button" title={t('content.moveUp')} disabled={i === 0} onClick={() => moveSpan(i, -1)}>
                  ↑
                </button>
                <button type="button" title={t('content.moveDown')} disabled={i >= list.length - 1} onClick={() => moveSpan(i, 1)}>
                  ↓
                </button>
                <button type="button" className="danger" title={t('content.deleteSpan')} onClick={() => removeSpan(i)}>
                  ✕
                </button>
              </div>
            </div>

            <div className="btn-row" style={{ alignItems: 'center' }}>
              <input
                type="text"
                style={{ flex: 1, minWidth: 0 }}
                value={span.text}
                onChange={(e) => update(i, { text: e.target.value })}
                placeholder={t('content.placeholder.text')}
              />
              {span.translate && onPickKey && (
                <button type="button" title={t('content.pickKey')} onClick={() => onPickKey((key) => update(i, { text: key, translate: true }))}>
                  键…
                </button>
              )}
            </div>

            <div className="btn-row">
              <label className="inline-check">
                <input type="checkbox" checked={!!span.style?.bold} onChange={() => toggleFlag(i, 'bold')} />
                粗体
              </label>
              <label className="inline-check">
                <input type="checkbox" checked={!!span.style?.italic} onChange={() => toggleFlag(i, 'italic')} />
                斜体
              </label>
              <label className="inline-check">
                <input type="checkbox" checked={!!span.style?.underline} onChange={() => toggleFlag(i, 'underline')} />
                下划线
              </label>
              <label className="inline-check">
                <input
                  type="checkbox"
                  checked={span.translate}
                  onChange={(e) => update(i, { translate: e.target.checked })}
                />
                翻译键
              </label>
            </div>

            <div className="field-grid span-meta">
              <div className="field">
                <label>颜色</label>
                <div className="color-inline">
                  <input
                    type="color"
                    value={colorHex || '#3F3F3F'}
                    onChange={(e) => {
                      const c = hexToColor(e.target.value);
                      updateStyle(i, { color: c });
                    }}
                    title={t('content.pickColor')}
                  />
                  <input
                    type="text"
                    className="mono"
                    placeholder="#RRGGBB"
                    value={colorHex}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      if (!v) {
                        updateStyle(i, { color: undefined });
                        return;
                      }
                      const c = hexToColor(v);
                      if (c != null) updateStyle(i, { color: c });
                    }}
                  />
                  {span.style?.color != null && (
                    <button type="button" className="ghost" onClick={() => updateStyle(i, { color: undefined })}>
                      清除
                    </button>
                  )}
                </div>
              </div>

              <div className="field">
                <label>font id</label>
                <input
                  type="text"
                  className="mono"
                  list="content-font-ids"
                  placeholder={FLEXIBOOK_DEFAULT_FONT}
                  value={span.style?.font || ''}
                  onChange={(e) => {
                    const v = e.target.value.trim();
                    updateStyle(i, { font: v || undefined });
                  }}
                />
              </div>
              <datalist id="content-font-ids">
                {fontIds.map((id) => (
                  <option key={id} value={id} />
                ))}
              </datalist>

              {fontExt && (
                <div className="banner banner-warn span-font-warn">
                  外部字体 <span className="mono">{span.style?.font}</span>：预览回退 unihex
                </div>
              )}

              <div className="field">
                <label>链接</label>
                <div className="link-row">
                  <select
                    value={linkKind}
                    onChange={(e) => {
                      const k = e.target.value as 'none' | 'command' | 'url';
                      if (k === 'none') setLink(i, 'none', '');
                      else if (k === 'command') setLink(i, 'command', linkValue || 'flexibook:example');
                      else setLink(i, 'url', linkValue || 'https://');
                    }}
                  >
                    <option value="none">无</option>
                    <option value="command">cmd</option>
                    <option value="url">url</option>
                  </select>
                  {linkKind !== 'none' && (
                    <input
                      type="text"
                      className="mono"
                      value={linkValue}
                      onChange={(e) => setLink(i, linkKind, e.target.value)}
                      placeholder={linkKind === 'command' ? 'namespace:id' : 'https://…'}
                    />
                  )}
                </div>
              </div>
            </div>
          </div>
        );
      })}
      <button type="button" onClick={addSpan}>
        + 添加 span
      </button>
    </div>
  );
}

/* ---------- Element list (recursive for box) ---------- */

interface ElementListProps {
  elements: BookElement[];
  onChange: (next: BookElement[]) => void;
  depth?: number;
  fontIds: string[];
  onPickKey?: (apply: (key: string) => void) => void;
}

function ElementListEditor({
  elements, onChange, depth = 0, fontIds, onPickKey }: ElementListProps) {
  const t = useT();
  function updateAt(idx: number, el: BookElement) {
    const arr = [...elements];
    arr[idx] = el;
    onChange(arr);
  }

  function removeAt(idx: number) {
    const arr = [...elements];
    arr.splice(idx, 1);
    onChange(arr);
  }

  function move(idx: number, dir: -1 | 1) {
    const arr = [...elements];
    const j = idx + dir;
    if (j < 0 || j >= arr.length) return;
    const tmp = arr[idx]!;
    arr[idx] = arr[j]!;
    arr[j] = tmp;
    onChange(arr);
  }

  function addElement(type: BookElement['type']) {
    let base: BookElement;
    if (type === 'heading') {
      base = {
        type: 'heading',
        level: 1,
        text: { key: 'flexibook.book.demo.h1', args: [] } as TranslatableText,
      };
    } else if (type === 'paragraph') {
      base = {
        type: 'paragraph',
        spans: [{ text: 'flexibook.book.demo.body1', translate: true, style: { ...SF.EMPTY } }],
      };
    } else if (type === 'bullet') {
      base = {
        type: 'bullet',
        spans: [{ text: 'flexibook.book.demo.body1', translate: true, style: { ...SF.EMPTY } }],
      };
    } else if (type === 'image') {
      base = {
        type: 'image',
        src: 'flexibook:textures/gui/icon.png',
        width: 48,
        height: 48,
        tooltip: 'flexibook.book.demo.image_tip',
      };
    } else if (type === 'br') {
      base = { type: 'br' };
    } else if (type === 'divider') {
      base = { type: 'divider' };
    } else {
      base = { type: 'box', children: [] };
    }
    onChange([...elements, base]);
  }

  return (
    <div className="element-list" data-depth={depth}>
      {depth === 0 && (
        <div className="toolbar">
          {ADD_BUTTONS.map((b) => (
            <button key={b.type} type="button" onClick={() => addElement(b.type)}>
              + {t(b.labelKey)}
            </button>
          ))}
        </div>
      )}
      {depth > 0 && (
        <div className="toolbar nested">
          {ADD_BUTTONS.map((b) => (
            <button key={b.type} type="button" onClick={() => addElement(b.type)}>
              + {t(b.labelKey)}
            </button>
          ))}
        </div>
      )}

      {elements.length === 0 && (
        <div className="empty-state">{depth > 0 ? t('content.emptyBox') : t('content.empty')}</div>
      )}

      {elements.map((el, idx) => (
        <div key={`${el.type}-${idx}-${depth}`} className="element-card">
          <div className="element-card-head">
            <span className="element-type">
              {typeLabel(el)}
              {el.type === 'heading' && <span className="badge">h{(el as { level?: number }).level || 1}</span>}
              {el.type === 'paragraph' || el.type === 'bullet' ? (
                <span className="badge">{el.spans?.length || 0} span</span>
              ) : null}
              {el.type === 'box' && <span className="badge">{el.children?.length || 0} 子</span>}
              <span className="badge">#{idx + 1}</span>
            </span>
            <div className="element-actions">
              <button type="button" title={t('content.moveUp')} disabled={idx === 0} onClick={() => move(idx, -1)}>
                ↑
              </button>
              <button
                type="button"
                title={t('content.moveDown')}
                disabled={idx >= elements.length - 1}
                onClick={() => move(idx, 1)}
              >
                ↓
              </button>
              <button type="button" className="danger" title={t('content.delete')} onClick={() => removeAt(idx)}>
                ✕
              </button>
            </div>
          </div>

          <div className="element-body">
            {el.type === 'heading' && (
              <>
                <div className="btn-row" style={{ alignItems: 'center' }}>
                  <input
                    type="text"
                    style={{ flex: 1, minWidth: 0 }}
                    value={el.text?.key || ''}
                    onChange={(e) =>
                      updateAt(idx, {
                        ...el,
                        text: { ...(el.text || { args: [] }), key: e.target.value },
                      })
                    }
                    placeholder={t('content.placeholder.title')}
                  />
                  {onPickKey && (
                    <button
                      type="button"
                      onClick={() =>
                        onPickKey((key) =>
                          updateAt(idx, {
                            ...el,
                            text: { ...(el.text || { args: [] }), key },
                          }),
                        )
                      }
                    >
                      键…
                    </button>
                  )}
                </div>
                <div className="field">
                  <label>level</label>
                  <select
                    value={el.level || 1}
                    onChange={(e) =>
                      updateAt(idx, {
                        ...el,
                        level: Math.min(2, Math.max(1, parseInt(e.target.value, 10) || 1)),
                      })
                    }
                  >
                    <option value={1}>h1</option>
                    <option value={2}>h2</option>
                  </select>
                </div>
                <div className="field">
                  <label>font</label>
                  <input
                    type="text"
                    className="mono"
                    list="content-font-ids"
                    placeholder={FLEXIBOOK_DEFAULT_FONT}
                    value={el.font || ''}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      const next = { ...el };
                      if (v) next.font = v;
                      else delete next.font;
                      updateAt(idx, next);
                    }}
                  />
                </div>
                {isExternalFont(el.font) && !fontIds.includes(el.font || '') && (
                  <div className="banner banner-warn">
                    外部字体 <span className="mono">{el.font}</span>：预览回退 unihex
                  </div>
                )}
              </>
            )}

            {(el.type === 'paragraph' || el.type === 'bullet') && (
              <SpanEditor
                spans={el.spans || []}
                onChange={(spans) => updateAt(idx, { ...el, spans })}
                fontIds={fontIds}
                onPickKey={onPickKey}
              />
            )}

            {el.type === 'image' && (
              <>
                <div className="field full">
                  <label>src</label>
                  <input
                    type="text"
                    className="mono"
                    value={el.src}
                    onChange={(e) => updateAt(idx, { ...el, src: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label>width</label>
                  <input
                    type="number"
                    value={el.width}
                    onChange={(e) => updateAt(idx, { ...el, width: parseInt(e.target.value, 10) || 0 })}
                  />
                </div>
                <div className="field">
                  <label>height</label>
                  <input
                    type="number"
                    value={el.height}
                    onChange={(e) => updateAt(idx, { ...el, height: parseInt(e.target.value, 10) || 0 })}
                  />
                </div>
                <div className="field full">
                  <label>tooltip key</label>
                  <input
                    type="text"
                    value={el.tooltip || ''}
                    onChange={(e) => {
                      const v = e.target.value;
                      updateAt(idx, { ...el, tooltip: v || undefined });
                    }}
                  />
                </div>
              </>
            )}

            {el.type === 'divider' && <div className="hint">水平分隔线</div>}
            {el.type === 'br' && <div className="hint">软换行</div>}

            {el.type === 'box' && (
              <>
                <div className="field full">
                  <label>className</label>
                  <input
                    type="text"
                    className="mono"
                    value={el.className || ''}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      updateAt(idx, { ...el, className: v || undefined });
                    }}
                    placeholder="note / tip / …"
                  />
                </div>
                <div className="box-children">
                  <div className="section-head">
                    <h4 className="section-title">{t('content.children')}</h4>
                    <span className="hint">{el.children?.length || 0} 项</span>
                  </div>
                  <ElementListEditor
                    elements={el.children || []}
                    onChange={(children) => updateAt(idx, { ...el, children })}
                    depth={depth + 1}
                    fontIds={fontIds}
                    onPickKey={onPickKey}
                  />
                </div>
              </>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

/* ---------- Main panel ---------- */

export function ContentPanel({
  content,
  onChange,
  lang,
  onResetDemo,
  onExport,
  onExportContentPack,
  onLoad,
  onSave,
  fontIds = [FLEXIBOOK_DEFAULT_FONT],
  langTables,
  onEnsureLangKey,
}: ContentPanelProps) {
  const t = useT();
  const elements = content.elements || [];
  const [keyPickerOpen, setKeyPickerOpen] = useState(false);
  const keyPickerApplyRef = useRef<((key: string) => void) | null>(null);

  function setTitleKey(key: string) {
    onChange({ ...content, title: { ...content.title, key } });
  }

  function setDefaultFont(font: string) {
    const v = font.trim();
    const next = { ...content };
    if (v) next.defaultFont = v;
    else delete next.defaultFont;
    onChange(next);
  }

  function openKeyPicker(apply: (key: string) => void) {
    keyPickerApplyRef.current = apply;
    setKeyPickerOpen(true);
  }

  const registered = new Set(fontIds);
  const unknownFonts = collectExternalFonts(content).filter((id) => !registered.has(id));
  const bookFontUnknown =
    !!content.defaultFont &&
    content.defaultFont !== FLEXIBOOK_DEFAULT_FONT &&
    !registered.has(content.defaultFont);

  return (
    <div>
      {(bookFontUnknown || unknownFonts.length > 0) && (
        <div className="banner banner-warn">
          <strong>未导入的外部字体</strong>
          <span>
            {' '}
            — 数据已保留，预览回退 <span className="mono">{FLEXIBOOK_DEFAULT_FONT}</span>
            {unknownFonts.length ? (
              <>
                ：<span className="mono">{unknownFonts.join(', ')}</span>
              </>
            ) : null}
          </span>
        </div>
      )}

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('content.section.title')}</h4>
        </div>
        <div className="field full">
          <label htmlFor="book-title">title key / 字面量</label>
          <div className="btn-row" style={{ alignItems: 'center' }}>
            <input
              id="book-title"
              type="text"
              style={{ flex: 1 }}
              value={content.title?.key || ''}
              onChange={(e) => setTitleKey(e.target.value)}
              placeholder={t('content.placeholder.orLiteral')}
            />
            {langTables && (
              <button type="button" onClick={() => openKeyPicker((k) => setTitleKey(k))}>
                键…
              </button>
            )}
          </div>
        </div>
        <div className="field full">
          <label htmlFor="book-font">defaultFont（可选覆盖）</label>
          <input
            id="book-font"
            type="text"
            className="mono"
            list="content-font-ids"
            value={content.defaultFont || ''}
            onChange={(e) => setDefaultFont(e.target.value)}
            placeholder={FLEXIBOOK_DEFAULT_FONT}
          />
          <datalist id="content-font-ids">
            {fontIds.map((id) => (
              <option key={id} value={id} />
            ))}
          </datalist>
        </div>
        <p className="section-hint">{t('content.titleHint')}</p>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">{t('content.section.elements')}</h4>
          <span className="hint">{elements.length} 项</span>
        </div>
        <ElementListEditor
          elements={elements}
          onChange={(next) => onChange({ ...content, elements: next })}
          fontIds={fontIds}
          onPickKey={langTables ? openKeyPicker : undefined}
        />
      </div>

      <p className="hint">
        当前语言 <strong style={{ color: 'var(--text-dim)' }}>{lang}</strong>
        。默认内容来自模组 <span className="mono">flexibook:demo_guide</span>。
        切换语言会重新加载翻译并触发布局。结构化元素编辑；译文请用顶栏「内容编辑」模式。
      </p>

      <div className="toolbar sticky-actions">
        {onLoad && (
          <button type="button" onClick={onLoad} title={t('content.openTitle')}>
            打开…
          </button>
        )}
        {onSave && (
          <button type="button" onClick={onSave} title={t('content.saveTitle')}>
            保存…
          </button>
        )}
        {onResetDemo && (
          <button type="button" onClick={onResetDemo} title={t('content.resetDemoTitle')}>
            重置为模组模板
          </button>
        )}
        {onExport && (
          <button type="button" onClick={onExport}>
            导出内容 JSON
          </button>
        )}
        {onExportContentPack && (
          <button
            type="button"
            className="primary"
            onClick={onExportContentPack}
            title={t('content.exportPackTitle')}
          >
            导出内容资源包…
          </button>
        )}
      </div>

      {keyPickerOpen && langTables && (
        <LangKeyPickerModal
          tables={langTables}
          onPick={(key) => {
            keyPickerApplyRef.current?.(key);
            keyPickerApplyRef.current = null;
          }}
          onCreateAndPick={(key) => {
            onEnsureLangKey?.(key);
            keyPickerApplyRef.current?.(key);
            keyPickerApplyRef.current = null;
          }}
          onClose={() => {
            setKeyPickerOpen(false);
            keyPickerApplyRef.current = null;
          }}
        />
      )}
    </div>
  );
}

function collectExternalFonts(content: AdaptiveBookContent): string[] {
  const found = new Set<string>();
  if (isExternalFont(content.defaultFont)) found.add(content.defaultFont!);
  const walk = (els?: BookElement[]) => {
    if (!els) return;
    for (const el of els) {
      if (el.type === 'heading' && isExternalFont(el.font)) found.add(el.font!);
      if (el.type === 'paragraph' || el.type === 'bullet') {
        for (const s of el.spans || []) {
          if (isExternalFont(s.style?.font)) found.add(s.style.font!);
        }
      }
      if (el.type === 'box') walk(el.children);
    }
  };
  walk(content.elements);
  return [...found];
}
