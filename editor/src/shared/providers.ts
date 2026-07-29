/**
 * 翻译提供者接口 - 镜像 Java 的 TranslationProvider
 */
export interface TranslationProvider {
  get(key: string, ...args: any[]): string;
  /** Optional: loaded language id for layout cache identity */
  getLoadedLang?(): string | null;
}

/**
 * 文本测量接口 - 镜像 Java 的 TextMeasurer
 */
export interface TextMeasurer {
  width(text: string, style: import('./types').StyleFlags, fontId?: string): number;
}
