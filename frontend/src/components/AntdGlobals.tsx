import { App } from 'antd';
import { ui } from '../utils/globals';

export default function AntdGlobals() {
  const a = App.useApp();
  ui.message = a.message;
  ui.notification = a.notification;
  ui.modal = a.modal;
  return null;
}
