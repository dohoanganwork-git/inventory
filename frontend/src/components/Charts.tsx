import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Empty } from 'antd';

// Bảng màu: một chuỗi dữ liệu dùng slot 1 (xanh) của bảng màu tham chiếu; lưới và trục mờ.
export const SERIES_1 = '#2a78d6';
const GRID = '#e8e8e6';
const AXIS = '#6b6a66';

interface Datum { [k: string]: string | number | undefined }

function TooltipBox({ active, payload, label, valueLabel, format }: {
  active?: boolean; payload?: { value: number }[]; label?: string; valueLabel: string; format: (v: number) => string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{label}</div>
      <div><span className="chart-swatch" />{valueLabel}: <b>{format(payload[0].value)}</b></div>
    </div>
  );
}

/** Biểu đồ cột dọc một chuỗi (ví dụ: số lượng bán theo ngày). */
export function ColumnChart({ data, xKey, yKey, valueLabel, format = (v) => v.toLocaleString('vi-VN'), height = 260, tickFormat }: {
  data: Datum[]; xKey: string; yKey: string; valueLabel: string; format?: (v: number) => string; height?: number;
  tickFormat?: (v: string) => string;
}) {
  if (!data.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có dữ liệu" />;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barCategoryGap="22%">
        <CartesianGrid vertical={false} stroke={GRID} />
        <XAxis dataKey={xKey} tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={{ stroke: GRID }}
          tickFormatter={tickFormat} interval="preserveStartEnd" minTickGap={8} />
        <YAxis tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} width={56}
          tickFormatter={(v: number) => format(v)} allowDecimals={false} />
        <Tooltip cursor={{ fill: 'rgba(42,120,214,0.08)' }}
          content={<TooltipBox valueLabel={valueLabel} format={format} />}
          labelFormatter={(l) => (tickFormat ? tickFormat(String(l)) : String(l))} />
        <Bar isAnimationActive={false} dataKey={yKey} fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={36} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Biểu đồ thanh ngang một chuỗi (xếp hạng mặt hàng, nhà cung cấp...). */
export function RankChart({ data, labelKey, valueKey, valueLabel, format = (v) => v.toLocaleString('vi-VN'), height }: {
  data: Datum[]; labelKey: string; valueKey: string; valueLabel: string; format?: (v: number) => string; height?: number;
}) {
  if (!data.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có dữ liệu" />;
  const h = height ?? Math.max(160, data.length * 36 + 24);
  return (
    <ResponsiveContainer width="100%" height={h}>
      <BarChart data={data} layout="vertical" margin={{ top: 4, right: 56, left: 4, bottom: 4 }} barCategoryGap="28%">
        <CartesianGrid horizontal={false} stroke={GRID} />
        <XAxis type="number" tick={{ fill: AXIS, fontSize: 12 }} tickLine={false} axisLine={false} tickFormatter={(v: number) => format(v)} />
        <YAxis type="category" dataKey={labelKey} width={170} tick={{ fill: '#3a3936', fontSize: 12 }} tickLine={false} axisLine={false}
          tickFormatter={(v: string) => (v.length > 26 ? v.slice(0, 25) + '…' : v)} />
        <Tooltip cursor={{ fill: 'rgba(42,120,214,0.08)' }} content={<TooltipBox valueLabel={valueLabel} format={format} />} />
        <Bar isAnimationActive={false} dataKey={valueKey} fill={SERIES_1} radius={[0, 4, 4, 0]} maxBarSize={22}
          label={{ position: 'right', fill: '#3a3936', fontSize: 12, formatter: (v: number) => format(v) }} />
      </BarChart>
    </ResponsiveContainer>
  );
}
