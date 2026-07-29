import type { TranslationProvider } from './providers';

/**
 * 从 JSON 语言文件加载翻译的提供者
 * 语言文件路径应在构建时拷贝到 editor 可访问的位置
 */
export class JsonTranslationProvider implements TranslationProvider {
  private data: Record<string, string> = {};
  private loadedLang: string | null = null;

  constructor() {}

  /**
   * 加载指定语言的 JSON（键值对）
   * @param lang 语言代码，如 'en_us', 'zh_cn'
   * @param json 解析后的对象
   */
  load(lang: string, json: Record<string, string>): void {
    this.data = { ...json };
    this.loadedLang = lang;
  }

  /**
   * 从 URL 异步加载语言文件（开发/运行时）
   * 期望的路径示例：/assets/lang/zh_cn.json 或 ./assets/lang/en_us.json
   */
  async loadFromUrl(lang: string, url: string): Promise<void> {
    const res = await fetch(url, { cache: 'no-cache' });
    if (!res.ok) throw new Error(`加载语言失败: ${url}`);
    const json = await res.json();
    this.load(lang, json);
  }

  get(key: string, ...args: any[]): string {
    const raw = this.data[key];
    if (raw == null) {
      // 回退：返回 key 本身
      if (args.length === 0) return key;
      // 简单格式化 %s
      return formatFallback(key, args);
    }
    if (args.length === 0) return raw;
    return format(raw, args);
  }

  getLoadedLang(): string | null {
    return this.loadedLang;
  }
}

function format(str: string, args: any[]): string {
  // 支持 %s 和 {0} 两种占位
  let out = str;
  let idx = 0;
  out = out.replace(/%s/g, () => String(args[idx++] ?? ''));
  // {0}, {1}, ...
  out = out.replace(/\{(\d+)\}/g, (_, n) => {
    const i = parseInt(n, 10);
    return String(args[i] ?? '');
  });
  return out;
}

function formatFallback(key: string, args: any[]): string {
  if (args.length === 0) return key;
  return key + ' ' + args.map(a => String(a)).join(' ');
}
