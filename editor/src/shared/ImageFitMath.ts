/**
 * Pure geometry for CONTAIN (same algorithm as Java ImageFitMath).
 * Used in the editor preview for aspect-preserving image scaling.
 */
export interface Fit {
  offsetX: number;
  offsetY: number;
  drawW: number;
  drawH: number;
}

export function contain(boxW: number, boxH: number, texW: number, texH: number): Fit {
  if (boxW <= 0 || boxH <= 0 || texW <= 0 || texH <= 0) {
    return { offsetX: 0, offsetY: 0, drawW: Math.max(0, boxW), drawH: Math.max(0, boxH) };
  }
  const scale = Math.min(boxW / texW, boxH / texH);
  let drawW = Math.max(1, Math.round(texW * scale));
  let drawH = Math.max(1, Math.round(texH * scale));
  drawW = Math.min(drawW, boxW);
  drawH = Math.min(drawH, boxH);
  const ox = Math.floor((boxW - drawW) / 2);
  const oy = Math.floor((boxH - drawH) / 2);
  return { offsetX: ox, offsetY: oy, drawW, drawH };
}
