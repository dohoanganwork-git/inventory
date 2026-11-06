import dayjs from 'dayjs';

const nf = new Intl.NumberFormat('vi-VN');

export const fmtNumber = (v?: number | null) => (v === undefined || v === null ? '—' : nf.format(v));
export const fmtMoney = (v?: number | null) => (v === undefined || v === null ? '—' : `${nf.format(Math.round(v))} đ`);
export const fmtMoneyShort = (v?: number | null) => {
  if (v === undefined || v === null) return '—';
  const a = Math.abs(v);
  if (a >= 1e9) return `${(v / 1e9).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tỷ`;
  if (a >= 1e6) return `${(v / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} tr`;
  if (a >= 1e3) return `${(v / 1e3).toLocaleString('vi-VN', { maximumFractionDigits: 0 })} N`;
  return nf.format(v);
};
export const fmtDate = (v?: string | null) => (v ? dayjs(v).format('DD/MM/YYYY') : '—');
export const fmtDateTime = (v?: string | null) => (v ? dayjs(v).format('DD/MM/YYYY HH:mm') : '—');
export const fmtSize = (n: number) => (n > 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`);
export const toIso = (d?: dayjs.Dayjs | null) => (d ? d.format('YYYY-MM-DD') : undefined);

/** Lọc chuỗi tiếng Việt không phân biệt dấu. */
export const normalize = (s?: string) =>
  (s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
