import { useCallback, useEffect, useRef, useState } from 'react';
import { api, showError } from '../api/client';

/** Tải dữ liệu GET từ API, kèm trạng thái loading và hàm tải lại. */
export function useFetch<T>(url: string | null, params?: Record<string, unknown>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(!!url);
  const key = JSON.stringify(params ?? {});
  const seq = useRef(0);

  const reload = useCallback(async () => {
    if (!url) return;
    const my = ++seq.current;
    setLoading(true);
    try {
      const res = await api.get<T>(url, { params: JSON.parse(key) });
      if (my === seq.current) setData(res.data);
    } catch (e) {
      if (my === seq.current) showError(e);
    } finally {
      if (my === seq.current) setLoading(false);
    }
  }, [url, key]);

  useEffect(() => { reload(); }, [reload]);
  return { data, loading, reload, setData };
}
