/** Editor-only texture overrides (not part of BookTheme JSON). */

export interface CustomTexture {
  /** blob: or data: URL used for live preview */
  url: string;
  /** Original file name (for future pack export) */
  fileName: string;
  naturalWidth: number;
  naturalHeight: number;
  /** Raw image bytes for pack export (populated by loadImageFile). */
  bytes?: ArrayBuffer;
}

export type TextureSlot = 'book' | 'widgets';

export interface CustomTextures {
  book: CustomTexture | null;
  widgets: CustomTexture | null;
}

export const EMPTY_CUSTOM_TEXTURES: CustomTextures = {
  book: null,
  widgets: null,
};

/** Resolve a flexibook:/assets path to a URL Vite can fetch. */
export function resolveThemeAssetUrl(key: string): string {
  if (!key) return '';
  if (key.startsWith('blob:') || key.startsWith('data:') || key.startsWith('http')) {
    return key;
  }
  if (key.includes(':')) {
    const [, p] = key.split(':');
    return new URL(`../../assets/${p}`, import.meta.url).toString();
  }
  if (key.startsWith('assets/') || key.startsWith('./assets/')) {
    return new URL(key.replace(/^\.\//, ''), import.meta.url).toString();
  }
  return new URL(`../../assets/${key.replace(/^\/+/, '')}`, import.meta.url).toString();
}

export function revokeCustomTexture(tex: CustomTexture | null | undefined) {
  if (tex?.url?.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(tex.url);
    } catch {
      /* ignore */
    }
  }
}

export function revokeAllCustomTextures(map: CustomTextures) {
  revokeCustomTexture(map.book);
  revokeCustomTexture(map.widgets);
}

/**
 * Read a local image file into a CustomTexture (object URL + natural size + bytes for export).
 */
export async function loadImageFile(file: File): Promise<CustomTexture> {
  const url = URL.createObjectURL(file);
  // Read bytes early; image load is only for dimensions.
  const bytes = await file.arrayBuffer().catch(() => undefined);

  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      resolve({
        url,
        fileName: file.name || 'texture.png',
        naturalWidth: img.naturalWidth || img.width,
        naturalHeight: img.naturalHeight || img.height,
        bytes: bytes instanceof ArrayBuffer ? bytes : undefined,
      });
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error(`Failed to load image: ${file.name}`));
    };
    img.src = url;
  });
}

/** Build CustomTexture from raw PNG/JPEG bytes (pack import / draft restore). */
export async function loadImageFromBytes(
  bytes: ArrayBuffer,
  fileName = 'texture.png',
): Promise<CustomTexture> {
  const url = URL.createObjectURL(new Blob([bytes]));
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      resolve({
        url,
        fileName,
        naturalWidth: img.naturalWidth || img.width,
        naturalHeight: img.naturalHeight || img.height,
        bytes,
      });
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error(`Failed to load image bytes: ${fileName}`));
    };
    img.src = url;
  });
}
