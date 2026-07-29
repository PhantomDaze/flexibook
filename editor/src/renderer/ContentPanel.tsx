import type { AdaptiveBookContent, BookElement, TranslatableText } from '../shared/types';
import { StyleFlags } from '../shared/types';

export interface ContentPanelProps {
  content: AdaptiveBookContent;
  onChange: (next: AdaptiveBookContent) => void;
  lang: string;
  onResetDemo?: () => void;
  onExport?: () => void;
}

const ADD_BUTTONS: { type: BookElement['type']; label: string }[] = [
  { type: 'paragraph', label: '段落' },
  { type: 'heading', label: '标题' },
  { type: 'bullet', label: '列表项' },
  { type: 'divider', label: '分隔线' },
  { type: 'br', label: '换行' },
  { type: 'image', label: '图片' },
];

function typeLabel(el: BookElement): string {
  switch (el.type) {
    case 'heading':
      return `heading`;
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

export function ContentPanel({ content, onChange, lang, onResetDemo, onExport }: ContentPanelProps) {
  function setTitleKey(key: string) {
    onChange({ ...content, title: { ...content.title, key } });
  }

  function updateElements(next: BookElement[]) {
    onChange({ ...content, elements: next });
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
        spans: [{ text: 'flexibook.book.demo.body1', translate: true, style: StyleFlags.EMPTY }],
      };
    } else if (type === 'bullet') {
      base = {
        type: 'bullet',
        spans: [{ text: 'flexibook.book.demo.body1', translate: true, style: StyleFlags.EMPTY }],
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
    updateElements([...(content.elements || []), base]);
  }

  function removeAt(idx: number) {
    const arr = [...(content.elements || [])];
    arr.splice(idx, 1);
    updateElements(arr);
  }

  function move(idx: number, dir: -1 | 1) {
    const arr = [...(content.elements || [])];
    const j = idx + dir;
    if (j < 0 || j >= arr.length) return;
    const tmp = arr[idx]!;
    arr[idx] = arr[j]!;
    arr[j] = tmp;
    updateElements(arr);
  }

  function editHeadingText(idx: number, key: string) {
    const arr = [...(content.elements || [])];
    const el = arr[idx];
    if (!el || el.type !== 'heading') return;
    arr[idx] = { ...el, text: { ...(el.text || { args: [] }), key } };
    updateElements(arr);
  }

  function editHeadingLevel(idx: number, level: number) {
    const arr = [...(content.elements || [])];
    const el = arr[idx];
    if (!el || el.type !== 'heading') return;
    arr[idx] = { ...el, level: Math.min(2, Math.max(1, level)) };
    updateElements(arr);
  }

  function editParagraphFirstSpan(idx: number, text: string, translate: boolean) {
    const arr = [...(content.elements || [])];
    const el = arr[idx];
    if (!el || (el.type !== 'paragraph' && el.type !== 'bullet')) return;
    const spans = el.spans?.length
      ? [{ ...el.spans[0]!, text, translate }]
      : [{ text, translate, style: StyleFlags.EMPTY }];
    arr[idx] = { ...el, spans };
    updateElements(arr);
  }

  function toggleStyle(idx: number, flag: 'bold' | 'italic' | 'underline') {
    const arr = [...(content.elements || [])];
    const el = arr[idx];
    if (!el || (el.type !== 'paragraph' && el.type !== 'bullet')) return;
    if (!el.spans?.length) return;
    const s = el.spans[0]!;
    const style = { ...s.style, [flag]: !s.style?.[flag] };
    arr[idx] = { ...el, spans: [{ ...s, style }] };
    updateElements(arr);
  }

  function editImage(idx: number, patch: Partial<{ src: string; width: number; height: number }>) {
    const arr = [...(content.elements || [])];
    const el = arr[idx];
    if (!el || el.type !== 'image') return;
    arr[idx] = { ...el, ...patch };
    updateElements(arr);
  }

  const elements = content.elements || [];

  return (
    <div>
      <div className="section">
        <div className="section-head">
          <h4 className="section-title">书标题</h4>
        </div>
        <div className="field full">
          <label htmlFor="book-title">title key / 字面量</label>
          <input
            id="book-title"
            type="text"
            value={content.title?.key || ''}
            onChange={(e) => setTitleKey(e.target.value)}
            placeholder="translation.key 或直接文字"
          />
        </div>
        <p className="section-hint">带点号的键会当作翻译键；否则作为字面量。</p>
      </div>

      <div className="section">
        <div className="section-head">
          <h4 className="section-title">内容元素</h4>
          <span className="hint">{elements.length} 项</span>
        </div>

        <div className="toolbar">
          {ADD_BUTTONS.map((b) => (
            <button key={b.type} type="button" onClick={() => addElement(b.type)}>
              + {b.label}
            </button>
          ))}
        </div>

        <div className="element-list">
          {elements.length === 0 && (
            <div className="empty-state">暂无内容。添加元素后会立即重排预览。</div>
          )}

          {elements.map((el, idx) => (
            <div key={`${el.type}-${idx}`} className="element-card">
              <div className="element-card-head">
                <span className="element-type">
                  {typeLabel(el)}
                  {el.type === 'heading' && (
                    <span className="badge">h{(el as { level?: number }).level || 1}</span>
                  )}
                  <span className="badge">#{idx + 1}</span>
                </span>
                <div className="element-actions">
                  <button type="button" title="上移" disabled={idx === 0} onClick={() => move(idx, -1)}>
                    ↑
                  </button>
                  <button
                    type="button"
                    title="下移"
                    disabled={idx >= elements.length - 1}
                    onClick={() => move(idx, 1)}
                  >
                    ↓
                  </button>
                  <button type="button" className="danger" title="删除" onClick={() => removeAt(idx)}>
                    ✕
                  </button>
                </div>
              </div>

              <div className="element-body">
                {el.type === 'heading' && (
                  <>
                    <input
                      type="text"
                      value={el.text?.key || ''}
                      onChange={(e) => editHeadingText(idx, e.target.value)}
                      placeholder="标题键或字面量"
                    />
                    <div className="field">
                      <label>level</label>
                      <select
                        value={el.level || 1}
                        onChange={(e) => editHeadingLevel(idx, parseInt(e.target.value, 10) || 1)}
                      >
                        <option value={1}>h1</option>
                        <option value={2}>h2</option>
                      </select>
                    </div>
                  </>
                )}

                {(el.type === 'paragraph' || el.type === 'bullet') && (
                  <>
                    <input
                      type="text"
                      value={el.spans?.[0]?.text || ''}
                      onChange={(e) =>
                        editParagraphFirstSpan(
                          idx,
                          e.target.value,
                          el.spans?.[0]?.translate ?? true,
                        )
                      }
                      placeholder="文本键或字面量"
                    />
                    <div className="btn-row">
                      <label className="inline-check">
                        <input
                          type="checkbox"
                          checked={!!el.spans?.[0]?.style?.bold}
                          onChange={() => toggleStyle(idx, 'bold')}
                        />
                        粗体
                      </label>
                      <label className="inline-check">
                        <input
                          type="checkbox"
                          checked={!!el.spans?.[0]?.style?.italic}
                          onChange={() => toggleStyle(idx, 'italic')}
                        />
                        斜体
                      </label>
                      <label className="inline-check">
                        <input
                          type="checkbox"
                          checked={!!el.spans?.[0]?.style?.underline}
                          onChange={() => toggleStyle(idx, 'underline')}
                        />
                        下划线
                      </label>
                      <label className="inline-check">
                        <input
                          type="checkbox"
                          checked={el.spans?.[0]?.translate ?? true}
                          onChange={(e) =>
                            editParagraphFirstSpan(
                              idx,
                              el.spans?.[0]?.text || '',
                              e.target.checked,
                            )
                          }
                        />
                        翻译键
                      </label>
                    </div>
                  </>
                )}

                {el.type === 'image' && (
                  <>
                    <div className="field full">
                      <label>src</label>
                      <input
                        type="text"
                        className="mono"
                        value={el.src}
                        onChange={(e) => editImage(idx, { src: e.target.value })}
                      />
                    </div>
                    <div className="field">
                      <label>width</label>
                      <input
                        type="number"
                        value={el.width}
                        onChange={(e) =>
                          editImage(idx, { width: parseInt(e.target.value, 10) || 0 })
                        }
                      />
                    </div>
                    <div className="field">
                      <label>height</label>
                      <input
                        type="number"
                        value={el.height}
                        onChange={(e) =>
                          editImage(idx, { height: parseInt(e.target.value, 10) || 0 })
                        }
                      />
                    </div>
                  </>
                )}

                {el.type === 'divider' && <div className="hint">水平分隔线</div>}
                {el.type === 'br' && <div className="hint">软换行</div>}
              </div>
            </div>
          ))}
        </div>
      </div>

      <p className="hint">
        当前语言 <strong style={{ color: 'var(--text-dim)' }}>{lang}</strong>
        。默认内容来自模组 <span className="mono">flexibook:demo_guide</span>。
        切换语言会重新加载翻译并触发布局。
      </p>

      {(onResetDemo || onExport) && (
        <div className="toolbar sticky-actions">
          {onResetDemo && (
            <button type="button" onClick={onResetDemo} title="恢复模组 demo_guide.json">
              重置为模组模板
            </button>
          )}
          {onExport && (
            <button type="button" className="primary" onClick={onExport}>
              导出内容 JSON
            </button>
          )}
        </div>
      )}
    </div>
  );
}
