import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import {
  createTranslator,
  loadUiLocale,
  saveUiLocale,
  UI_LOCALE_LABELS,
  UI_LOCALES,
  type TranslateFn,
  type UiLocale,
} from '../shared/uiI18n';

export interface UiI18nValue {
  locale: UiLocale;
  setLocale: (locale: UiLocale) => void;
  t: TranslateFn;
  locales: UiLocale[];
  labels: Record<UiLocale, string>;
}

const UiI18nContext = createContext<UiI18nValue | null>(null);

export function UiI18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<UiLocale>(() => loadUiLocale());
  const value = useMemo<UiI18nValue>(() => {
    const t = createTranslator(locale);
    return {
      locale,
      setLocale: (next) => {
        setLocaleState(next);
        saveUiLocale(next);
      },
      t,
      locales: UI_LOCALES,
      labels: UI_LOCALE_LABELS,
    };
  }, [locale]);
  return <UiI18nContext.Provider value={value}>{children}</UiI18nContext.Provider>;
}

export function useUiI18n(): UiI18nValue {
  const ctx = useContext(UiI18nContext);
  if (!ctx) {
    // Safe fallback when used outside provider (tests / story)
    const locale = loadUiLocale();
    return {
      locale,
      setLocale: () => undefined,
      t: createTranslator(locale),
      locales: UI_LOCALES,
      labels: UI_LOCALE_LABELS,
    };
  }
  return ctx;
}

/** Shorthand */
export function useT(): TranslateFn {
  return useUiI18n().t;
}
