import { useEffect, useRef, useState } from 'react';
import type { UnihexFont } from '../shared/UnihexFont';
import { MC_CELL } from '../shared/UnihexFont';
import type { BookTheme, RenderedPage } from '../shared/types';
import { StyleFlags } from '../shared/types';
import { contain } from '../shared/ImageFitMath';

export interface PreviewCanvasProps {
  pages: RenderedPage[];
  pageIndex: number;
  theme: BookTheme;
  scale: 1 | 2 | 3 | 4;
  widgetsImg: HTMLImageElement | null;
  widgetsReady: boolean;
  bookImg: HTMLImageElement | null;
  bookReady: boolean;
  /** FlexiBook unihex measurer/renderer (same ZIP as the mod). */
  atlasMeasurer?: UnihexFont | null;
  fontRev?: number;
  /** 已按 flexibook.screen.page 翻译的页码文本，与游戏端 Component.translatable 对齐 */
  pageLabel: string;
  /** 已翻译的书标题；与 AdaptiveBookScreen 标题绘制对齐 */
  titleLabel?: string;
  onPrev: () => void;
  onNext: () => void;
}

function resolveAssetUrl(key: string): string {
  if (key.includes(':')) {
    const [, p] = key.split(':');
    return new URL(`../../assets/${p}`, import.meta.url).toString();
  }
  if (key.startsWith('assets/') || key.startsWith('./assets/')) {
    return new URL(key.replace(/^\.\//, ''), import.meta.url).toString();
  }
  return new URL(`../../assets/${key.replace(/^\/+/, '')}`, import.meta.url).toString();
}

function colorHex(rgb: number): string {
  return '#' + (rgb & 0xffffff).toString(16).padStart(6, '0');
}

export function PreviewCanvas(props: PreviewCanvasProps) {
  const {
    pages,
    pageIndex,
    theme,
    scale,
    widgetsImg,
    widgetsReady,
    bookImg,
    bookReady,
    atlasMeasurer,
    fontRev,
    pageLabel,
    titleLabel = '',
    onPrev,
    onNext,
  } = props;

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawW = Math.floor(theme.bookTexWidth * scale);
  const drawH = Math.floor(theme.bookTexHeight * scale);

  const [imgVersion, setImgVersion] = useState(0);
  const loadedImagesRef = useRef<Map<string, HTMLImageElement>>(new Map());

  function getOrLoadImage(key: string): HTMLImageElement | null {
    if (!key) return null;
    const cached = loadedImagesRef.current.get(key);
    if (cached) return cached;
    const img = new Image();
    img.onload = () => {
      loadedImagesRef.current.set(key, img);
      setImgVersion((v) => v + 1);
    };
    img.onerror = () => {
      /* keep placeholder */
    };
    img.src = resolveAssetUrl(key);
    return null;
  }

  useEffect(() => {
    const c = canvasRef.current;
    if (!c) return;
    c.width = drawW + 80;
    c.height = drawH + 60;
    const g = c.getContext('2d', { alpha: true });
    if (!g) return;

    g.clearRect(0, 0, c.width, c.height);

    const ox = 40;
    const oy = 20;

    if (bookReady && bookImg) {
      g.imageSmoothingEnabled = false;
      // Vanilla book sheet: sample top-left panel region.
      // Custom full-bleed images often equal natural size == bookTex*; if image is
      // smaller/larger than the declared panel, fall back to full-image stretch.
      const srcW = theme.bookTexWidth;
      const srcH = theme.bookTexHeight;
      if (bookImg.width >= srcW && bookImg.height >= srcH) {
        g.drawImage(bookImg, 0, 0, srcW, srcH, ox, oy, drawW, drawH);
      } else {
        g.drawImage(bookImg, 0, 0, bookImg.width, bookImg.height, ox, oy, drawW, drawH);
      }
    } else {
      g.fillStyle = '#f6f0df';
      g.fillRect(ox, oy, drawW, drawH);
      g.strokeStyle = '#8b7355';
      g.lineWidth = Math.max(1, scale);
      g.strokeRect(ox + 0.5, oy + 0.5, drawW, drawH);
    }

    const cx = ox + Math.floor(theme.contentLeft * scale);
    const cy = oy + Math.floor((theme.contentTop + theme.contentOffsetY) * scale);

    const page = pages[pageIndex];
    if (page) {
      for (const el of page.elements) {
        if (el.kind === 'text') {
          const x = cx + Math.floor(el.x * scale);
          const y = cy + Math.floor(el.y * scale);
          const sc = (el.scale ?? 1) * scale;

          if (el.highlight) {
            const w = Math.max(1, Math.floor((el.width ?? 10) * sc));
            const h = Math.floor((el.height ?? theme.lineHeight) * sc);
            g.fillStyle = 'rgba(255, 213, 79, 0.4)';
            g.fillRect(x - 1, y - 1, w + 2, h + 2);
          }

          let color = colorHex(theme.pageTextColor);
          if (el.style?.color != null) {
            color = colorHex(el.style.color);
          } else if (el.link) {
            color = colorHex(theme.linkColor);
          }

          g.save();
          g.fillStyle = color;
          g.textBaseline = 'top';

          const style = el.style ?? StyleFlags.EMPTY;
          const drawn =
            atlasMeasurer?.drawText?.(g, el.text ?? '', x, y, sc, style) ?? false;
          // No system-font fillText fallback — wait for unihex.

          if (el.style?.underline || el.link) {
            const logicalW =
              el.width ??
              (atlasMeasurer ? atlasMeasurer.width(el.text ?? '', style) : (el.text?.length || 0) * 6);
            const w = Math.max(1, Math.floor(logicalW * sc));
            g.strokeStyle = color;
            g.lineWidth = Math.max(1, Math.floor(sc));
            g.beginPath();
            g.moveTo(x, y + Math.floor(MC_CELL * sc) + 1);
            g.lineTo(x + w, y + Math.floor(MC_CELL * sc) + 1);
            g.stroke();
          }
          g.restore();
          void drawn;
        } else if (el.kind === 'divider') {
          const x = cx + Math.floor(el.x * scale);
          const y = cy + Math.floor((el.y + (el.height ?? 0) / 2) * scale);
          const w = Math.floor((el.width ?? theme.pageContentWidth) * scale);
          g.fillStyle = colorHex(theme.dividerColor);
          g.fillRect(x, y, w, Math.max(1, Math.floor(scale)));
        } else if (el.kind === 'image') {
          const boxX = cx + Math.floor(el.x * scale);
          const boxY = cy + Math.floor(el.y * scale);
          const boxW = Math.floor(el.width * scale);
          const boxH = Math.floor(el.height * scale);

          const key = el.texture || '';
          const img = getOrLoadImage(key);

          if (img) {
            g.imageSmoothingEnabled = false;
            if (theme.imageFit === 'contain') {
              const fit = contain(boxW, boxH, img.width, img.height);
              g.drawImage(
                img,
                0,
                0,
                img.width,
                img.height,
                boxX + fit.offsetX,
                boxY + fit.offsetY,
                fit.drawW,
                fit.drawH,
              );
            } else {
              g.drawImage(img, boxX, boxY, boxW, boxH);
            }
          } else {
            g.fillStyle = '#ddd';
            g.fillRect(boxX, boxY, Math.max(1, boxW), Math.max(1, boxH));
            g.strokeStyle = '#aaa';
            g.strokeRect(boxX + 0.5, boxY + 0.5, Math.max(1, boxW), Math.max(1, boxH));
          }
        }
      }
    }

    const labelStyle = StyleFlags.EMPTY;
    g.fillStyle = colorHex(theme.pageTextColor);
    g.textBaseline = 'top';

    // Book title — AdaptiveBookScreen:
    // titleX = leftPos + (bookTexWidth - font.width(title)) / 2
    // titleY = topPos + titleOffsetY
    if (titleLabel && atlasMeasurer) {
      const logicalTitleW = atlasMeasurer.width(titleLabel, labelStyle);
      const logicalTitleX = Math.trunc((theme.bookTexWidth - logicalTitleW) / 2);
      const tx = ox + logicalTitleX * scale;
      const ty = oy + theme.titleOffsetY * scale;
      atlasMeasurer.drawText(g, titleLabel, tx, ty, scale, labelStyle);
    }

    // Page label — AdaptiveBookScreen:
    // x = leftPos + (bookTexWidth - font.width(label)) / 2
    // y = topPos + bookTexHeight - pageLabelInsetY
    if (atlasMeasurer) {
      const logicalLabelW = atlasMeasurer.width(pageLabel, labelStyle);
      const logicalLabelX = Math.trunc((theme.bookTexWidth - logicalLabelW) / 2);
      const lx = ox + logicalLabelX * scale;
      const ly = oy + (theme.bookTexHeight - theme.pageLabelInsetY) * scale;
      atlasMeasurer.drawText(g, pageLabel, lx, ly, scale, labelStyle);
    }

    const btnY = oy + drawH + 6;
    const btnH = Math.max(14, Math.floor(16 * scale));
    const btnW = Math.max(28, Math.floor(32 * scale));
    const prevX = ox;
    const nextX = ox + drawW - btnW;

    if (widgetsReady && widgetsImg) {
      const srcW = widgetsImg.width / 2;
      const srcH = widgetsImg.height;
      g.imageSmoothingEnabled = false;
      g.drawImage(widgetsImg, 0, 0, srcW, srcH, prevX, btnY, btnW, btnH);
      g.drawImage(widgetsImg, srcW, 0, srcW, srcH, nextX, btnY, btnW, btnH);
    } else {
      g.fillStyle = '#555';
      g.fillRect(prevX, btnY, btnW, btnH);
      g.fillRect(nextX, btnY, btnW, btnH);
      g.fillStyle = '#fff';
      g.font = `${Math.max(10, Math.floor(10 * scale))}px sans-serif`;
      g.fillText('◀', prevX + 6, btnY + Math.floor(btnH * 0.7));
      g.fillText('▶', nextX + 6, btnY + Math.floor(btnH * 0.7));
    }
  }, [
    pages,
    pageIndex,
    theme,
    scale,
    drawW,
    drawH,
    widgetsImg,
    widgetsReady,
    bookImg,
    bookReady,
    imgVersion,
    atlasMeasurer,
    fontRev,
    pageLabel,
    titleLabel,
  ]);

  useEffect(() => {
    const c = canvasRef.current;
    if (!c) return;

    function onClick(e: MouseEvent) {
      const rect = c!.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;

      const ox = 40;
      const oy = 20;
      const btnY = oy + drawH + 6;
      const btnH = Math.max(14, Math.floor(16 * scale));
      const btnW = Math.max(28, Math.floor(32 * scale));
      const prevX = ox;
      const nextX = ox + drawW - btnW;

      if (my >= btnY && my <= btnY + btnH) {
        if (mx >= prevX && mx <= prevX + btnW) onPrev();
        else if (mx >= nextX && mx <= nextX + btnW) onNext();
      }
    }

    c.addEventListener('click', onClick);
    return () => c.removeEventListener('click', onClick);
  }, [drawH, drawW, scale, onPrev, onNext]);

  return (
    <div className="preview-frame">
      <canvas ref={canvasRef} />
    </div>
  );
}
