import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App as AntApp, ConfigProvider } from 'antd';
import viVN from 'antd/locale/vi_VN';
import dayjs from 'dayjs';
import 'dayjs/locale/vi';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import AntdGlobals from './components/AntdGlobals';
import './styles.css';

dayjs.locale('vi');

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: '#2a78d6',
          colorInfo: '#2a78d6',
          borderRadius: 8,
          fontFamily: "'Be Vietnam Pro', -apple-system, 'Segoe UI', Roboto, sans-serif",
          fontSize: 14,
          colorBgLayout: '#f4f6f9',
        },
        components: {
          Layout: { siderBg: '#0f1b33', headerBg: '#ffffff', headerHeight: 60, headerPadding: '0 16px' },
          Menu: { darkItemBg: '#0f1b33', darkSubMenuItemBg: '#0f1b33', darkItemSelectedBg: '#2a78d6', darkGroupTitleColor: '#7f8db0' },
          Table: { headerBg: '#f6f8fb', rowHoverBg: '#f2f7fd' },
          Card: { headerFontSize: 15 },
        },
      }}
    >
      <AntApp>
        <AntdGlobals />
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
