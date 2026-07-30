import { useCallback, useEffect, useMemo, useRef } from 'react';
import { tokensToHtml, tokenizeMarkup } from '../shared/markupHighlight';

export interface MarkupEditorProps {
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  rows?: number;
  className?: string;
  /** Stretch to fill parent height (content-edit workspace). */
  fill?: boolean;
  autoFocus?: boolean;
}

/**
 * Controlled raw markup editor with a mirrored syntax-highlight layer.
 * Zero deps — textarea over pre/code with identical font metrics.
 */
export function MarkupEditor({
  value,
  onChange,
  placeholder,
  rows = 8,
  className = '',
  fill = false,
  autoFocus = false,
}: MarkupEditorProps) {
  const taRef = useRef<HTMLTextAreaElement | null>(null);
  const preRef = useRef<HTMLPreElement | null>(null);

  const html = useMemo(() => {
    try {
      const tokens = tokenizeMarkup(value || '');
      const body = tokensToHtml(tokens);
      return body + (value.endsWith('\n') ? '\n' : '');
    } catch {
      return escapePlain(value || '');
    }
  }, [value]);

  const syncScroll = useCallback(() => {
    const ta = taRef.current;
    const pre = preRef.current;
    if (!ta || !pre) return;
    pre.scrollTop = ta.scrollTop;
    pre.scrollLeft = ta.scrollLeft;
  }, []);

  useEffect(() => {
    syncScroll();
  }, [value, syncScroll]);

  useEffect(() => {
    if (autoFocus) taRef.current?.focus();
  }, [autoFocus]);

  return (
    <div
      className={`markup-editor ${fill ? 'markup-editor-fill' : ''} ${className}`.trim()}
      style={fill ? undefined : { minHeight: rows * 18 }}
    >
      <pre
        ref={preRef}
        className="markup-highlight"
        aria-hidden
        dangerouslySetInnerHTML={{ __html: html || (placeholder ? '' : '\n') }}
      />
      <textarea
        ref={taRef}
        className="markup-textarea raw-markup"
        value={value}
        placeholder={placeholder}
        rows={fill ? undefined : rows}
        spellCheck={false}
        onChange={(e) => onChange(e.target.value)}
        onScroll={syncScroll}
      />
    </div>
  );
}

function escapePlain(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
