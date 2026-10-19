import React from 'react';
import ReactDOM from 'react-dom/client';
import { GoogleOAuthProvider } from '@react-oauth/google';
import './i18n';
import App from './App.tsx';
import LocalizedAntd from './components/LocalizedAntd';
import 'antd/dist/antd.css';
import './index.css';

// Thay YOUR_GOOGLE_CLIENT_ID bằng Client ID thật từ Google Cloud Console
// Xem hướng dẫn: https://console.cloud.google.com/apis/credentials
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || 'YOUR_GOOGLE_CLIENT_ID';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
      <LocalizedAntd>
        <App />
      </LocalizedAntd>
    </GoogleOAuthProvider>
  </React.StrictMode>,
);
