// Defaults sourced from the mod's data-driven demo content + themes.
// Content body: editor/assets/contents/demo_guide.json
//   ↔ src/main/resources/assets/flexibook/flexibook/contents/demo_guide.json
// Book index: editor/assets/books/demo_guide.json (content + theme refs)
// Themes: editor/assets/themes/

import type { AdaptiveBookContent, BookTheme } from '../shared/types';
import { parseBookContentJson, parseThemeJson } from '../shared/modJson';

import defaultThemeJson from '../../assets/themes/default.json';
import containThemeJson from '../../assets/themes/contain.json';
import demoGuideContentJson from '../../assets/contents/demo_guide.json';

export const DEFAULT_THEME: BookTheme = parseThemeJson(defaultThemeJson);
export const CONTAIN_THEME: BookTheme = parseThemeJson(containThemeJson);

/** Mod template content body (keys only; lang resolves at runtime). */
export const DEMO_GUIDE_CONTENT: AdaptiveBookContent = parseBookContentJson(demoGuideContentJson);

/** @deprecated use DEMO_GUIDE_CONTENT */
export const SAMPLE_CONTENT = DEMO_GUIDE_CONTENT;
