import axios, { AxiosError } from 'axios';
import { ui } from '../utils/globals';

const TOKEN_KEY = 'kho_token';
export const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '') + '/api';

export function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(t: string | null) {
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY);
  } catch { /* trình duyệt chặn lưu trữ */ }
}

export const api = axios.create({ baseURL: API_BASE, timeout: 60000 });

api.interceptors.request.use((cfg) => {
  const t = getToken();
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void) { onUnauthorized = fn; }

api.interceptors.response.use(
  (r) => r,
  (err: AxiosError) => {
    const url = err.config?.url ?? '';
    if (err.response?.status === 401 && !url.includes('/auth/login')) {
      setToken(null);
      onUnauthorized?.();
    }
    return Promise.reject(err);
  },
);

export interface ApiError { message: string; code?: string; details: string[]; status?: number }

async function readBlobError(data: Blob): Promise<Record<string, unknown> | null> {
  try { return JSON.parse(await data.text()); } catch { return null; }
}

export async function parseError(e: unknown): Promise<ApiError> {
  const ax = e as AxiosError<Record<string, unknown>>;
  if (ax?.isAxiosError) {
    if (!ax.response) return { message: 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.', details: [] };
    let d: Record<string, unknown> | null = ax.response.data as Record<string, unknown>;
    if (d instanceof Blob) d = await readBlobError(d);
    return {
      message: (d?.message as string) || 'Đã xảy ra lỗi, vui lòng thử lại',
      code: d?.code as string,
      details: (d?.details as string[]) || [],
      status: ax.response.status,
    };
  }
  return { message: (e as Error)?.message || 'Đã xảy ra lỗi', details: [] };
}

/** Hiển thị lỗi: thông báo ngắn hoặc danh sách chi tiết khi có nhiều lỗi. */
export async function showError(e: unknown) {
  const err = await parseError(e);
  if (err.status === 401) return;
  if (err.details.length > 1 || (err.details.length === 1 && err.details[0] !== err.message)) {
    ui.notification.error({
      message: err.message,
      description: (
        `${err.details.slice(0, 12).map((d) => '• ' + d).join('\n')}${err.details.length > 12 ? `\n… và ${err.details.length - 12} lỗi khác` : ''}`
      ),
      duration: 10,
      style: { whiteSpace: 'pre-line' },
    });
  } else {
    ui.message.error(err.message);
  }
}

function filenameFrom(disposition?: string, fallback = 'tep-tin') {
  if (!disposition) return fallback;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : fallback;
}

/** Tải file (có xác thực) và lưu về máy. */
export async function download(url: string, params?: Record<string, unknown>, fallbackName?: string) {
  try {
    const res = await api.get(url, { params, responseType: 'blob' });
    const name = filenameFrom(res.headers['content-disposition'] as string | undefined, fallbackName);
    const href = URL.createObjectURL(res.data as Blob);
    const a = document.createElement('a');
    a.href = href;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(href), 2000);
  } catch (e) {
    showError(e);
  }
}

/** Mở file trong tab mới (ảnh, PDF) – các loại khác sẽ tải về. */
export async function openFile(id: string, name: string, contentType?: string) {
  const viewable = contentType?.startsWith('image/') || contentType === 'application/pdf';
  if (!viewable) return download(`/files/${id}`, undefined, name);
  const win = window.open('', '_blank');
  try {
    const res = await api.get(`/files/${id}`, { params: { inline: true }, responseType: 'blob' });
    const href = URL.createObjectURL(res.data as Blob);
    if (win) win.location.href = href; else window.open(href, '_blank');
  } catch (e) {
    win?.close();
    showError(e);
  }
}

/** Gửi multipart: phần "data" là JSON, các phần còn lại là file. */
export function multipart(data: unknown, files: Record<string, File | undefined | null>) {
  const fd = new FormData();
  if (data !== undefined) fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  Object.entries(files).forEach(([k, f]) => { if (f) fd.append(k, f); });
  return fd;
}
