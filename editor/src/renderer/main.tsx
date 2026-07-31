import { createRoot } from 'react-dom/client';
import App from './App';
import { UiI18nProvider } from './UiI18n';

const el = document.getElementById('root');
if (!el) throw new Error('Missing #root');
createRoot(el).render(
  <UiI18nProvider>
    <App />
  </UiI18nProvider>,
);
