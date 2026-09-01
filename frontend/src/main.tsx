import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';
import './dashboard.css';

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Could not find #root element to mount React app');
}

createRoot(rootEl).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
