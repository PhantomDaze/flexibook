// Defaults sourced from the mod's data-driven demo book + themes.
// JSON lives under editor/assets/{books,themes}/ (synced from
// src/main/resources/assets/flexibook/flexibook/{books,themes}/).

import type { AdaptiveBookContent, BookTheme } from '../shared/types';
import { parseBookContentJson, parseThemeJson } from '../shared/modJson';

import defaultThemeJson from '../../assets/themes/default.json';
import containThemeJson from '../../assets/themes/contain.json';
import demoGuideJson from '../../assets/books/demo_guide.json';

export const DEFAULT_THEME: BookTheme = parseThemeJson(defaultThemeJson);
export const CONTAIN_THEME: BookTheme = parseThemeJson(containThemeJson);

/** Mod template book: assets/flexibook/flexibook/books/demo_guide.json */
export const DEMO_GUIDE_CONTENT: AdaptiveBookContent = parseBookContentJson(demoGuideJson);

/** @deprecated use DEMO_GUIDE_CONTENT */
export const SAMPLE_CONTENT = DEMO_GUIDE_CONTENT;
