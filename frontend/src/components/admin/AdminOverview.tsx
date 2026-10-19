import React, { useCallback, useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Button, message } from 'antd';
import {
  FileExcelOutlined,
  UserOutlined,
  BookOutlined,
  DollarOutlined,
  ReconciliationOutlined
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { adminApi, AdminOverview as OverviewStats } from '../../api';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend } from 'recharts';
import type { PieLabelRenderProps } from 'recharts';
import { cachedFetch } from '../../utils/apiCache';
import { getErrorMessage } from '../../utils/errors';

const CHART_MONTHS = 6;

interface MonthlyRevenue {
  month: number;
  year: number;
  revenue: number;
}

const renderCustomizedLabel = (props: PieLabelRenderProps) => {
  const percent = props.percent ?? 0;
  if (percent === 0) return null;
  const cx = Number(props.cx ?? 0);
  const cy = Number(props.cy ?? 0);
  const innerRadius = Number(props.innerRadius ?? 0);
  const outerRadius = Number(props.outerRadius ?? 0);
  const midAngle = props.midAngle ?? 0;
  const RADIAN = Math.PI / 180;
  const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);

  return (
    <text x={x} y={y} fill="white" textAnchor="middle" dominantBaseline="central" fontWeight="bold" fontSize={13}>
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
};

const AdminOverview: React.FC = () => {
  const { t } = useTranslation();
  const [stats, setStats] = useState<OverviewStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const [chartData, setChartData] = useState<MonthlyRevenue[]>([]);

  const pieData = [
    { name: t('admin.overview.pending'), value: stats?.pendingBookings ?? 0 },
    { name: t('admin.overview.confirmed'), value: stats?.confirmedBookings ?? 0 },
    { name: t('admin.overview.closed'), value: (stats?.cancelledBookings ?? 0) + (stats?.rejectedBookings ?? 0) },
  ];
  const chartRows = chartData.map(m => ({ ...m, name: t('admin.overview.month', { month: m.month }) }));
  const COLORS = ['#faad14', '#52c41a', '#ff4d4f'];

  const loadStats = useCallback(async () => {
    try {
      setLoading(true);
      // Cache stats 30 giây (hay thay đổi nên không cache lâu)
      setStats(await cachedFetch('admin_stats', () => adminApi.getOverview(), 30_000));

      // Cache danh sách booking 30 giây
      const bookings = await cachedFetch('admin_bookings_all', () => adminApi.getBookings(), 30_000);

      const months: MonthlyRevenue[] = Array.from({ length: CHART_MONTHS }).map((_, i) => {
        const d = new Date();
        d.setMonth(d.getMonth() - (CHART_MONTHS - 1 - i));
        return {
          month: d.getMonth() + 1,
          year: d.getFullYear(),
          revenue: 0
        };
      });

      (bookings || []).forEach(b => {
        const dateStr = b.checkInDate || b.createdAt;
        // Same definition as the paidRevenue total: only money actually received counts.
        if (b.paymentStatus === 'PAID' && dateStr) {
          const date = new Date(dateStr);
          const m = date.getMonth() + 1;
          const y = date.getFullYear();
          
          const targetMonth = months.find(x => x.month === m && x.year === y);
          if (targetMonth) {
            targetMonth.revenue += (b.totalPrice || 0);
          }
        }
      });
      setChartData(months);
    } catch (error) {
      message.error(t('admin.overview.loadFailed'));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const handleExportExcel = async () => {
    try {
      setExporting(true);
      // Tải file dạng blob qua API client để đi đúng proxy và tự gắn Auth header
      const blob = await adminApi.exportBookingsExcel();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `bookings_report_${new Date().toISOString().slice(0, 10)}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      
      message.success(t('admin.overview.exportSuccess'));
    } catch (error) {
      message.error(getErrorMessage(error, t('admin.overview.exportFailed')));
      console.error(error);
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return (
      <div>
        {/* Header skeleton */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <div style={{ height: 24, width: 200, borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
          <div style={{ height: 36, width: 160, borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
        </div>
        {/* Stat cards skeleton */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          {[...Array(4)].map((_, i) => (
            <Col xs={24} sm={12} md={6} key={i}>
              <div style={{ borderRadius: 12, background: '#fff', padding: '20px 24px', boxShadow: '0 4px 20px rgba(0,0,0,0.05)', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div style={{ height: 14, width: '60%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                <div style={{ height: 28, width: '40%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
              </div>
            </Col>
          ))}
        </Row>
        {/* Chart skeleton */}
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={16}>
            <div style={{ borderRadius: 12, background: '#fff', padding: 24, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: 340 }}>
              <div style={{ height: 18, width: '40%', borderRadius: 6, marginBottom: 24, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
              <div style={{ height: 260, borderRadius: 8, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
            </div>
          </Col>
          <Col xs={24} lg={8}>
            <div style={{ borderRadius: 12, background: '#fff', padding: 24, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: 340 }}>
              <div style={{ height: 18, width: '50%', borderRadius: 6, marginBottom: 24, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
              <div style={{ width: 180, height: 180, borderRadius: '50%', margin: '0 auto', background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
            </div>
          </Col>
        </Row>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>{t('admin.overview.title')}</h2>
        <Button 
          type="primary" 
          icon={<FileExcelOutlined />} 
          onClick={handleExportExcel}
          loading={exporting}
          style={{ background: '#107c41', borderColor: '#107c41' }}
        >
          {t('admin.overview.exportExcel')}
        </Button>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', background: 'linear-gradient(135deg, #f6ffed 0%, #e6f7ff 100%)' }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: '#595959' }}>{t('admin.overview.totalRevenue')}</span>}
              value={stats?.paidRevenue ?? 0}
              precision={0}
              valueStyle={{ color: '#3f8600', fontWeight: 800, fontSize: 28 }}
              prefix={<DollarOutlined />}
              suffix="₫"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: '#595959' }}>{t('admin.overview.totalBookings')}</span>}
              value={stats?.totalBookings || 0}
              valueStyle={{ color: '#006ce4', fontWeight: 800, fontSize: 28 }}
              prefix={<BookOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: '#595959' }}>{t('admin.overview.pendingBookings')}</span>}
              value={stats?.pendingBookings || 0}
              valueStyle={{ color: '#faad14', fontWeight: 800, fontSize: 28 }}
              prefix={<ReconciliationOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
            <Statistic
              title={<span style={{ fontWeight: 600, color: '#595959' }}>{t('admin.overview.pendingRequests')}</span>}
              value={stats?.pendingRequests || 0}
              valueStyle={{ color: '#cf1322', fontWeight: 800, fontSize: 28 }}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
      </Row>
      
      <Row gutter={[24, 24]} style={{ marginTop: 24 }}>
        <Col xs={24} lg={16}>
          <Card 
            bordered={false} 
            style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}
            title={<span style={{ fontSize: 16, fontWeight: 700 }}>{t('admin.overview.revenueChart')}</span>}
          >
            <div style={{ width: '100%', height: 300 }}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartRows} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorRevenue" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#1890ff" stopOpacity={0.3}/>
                      <stop offset="95%" stopColor="#1890ff" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{fill: '#8c8c8c'}} dy={10} />
                  <YAxis axisLine={false} tickLine={false} tick={{fill: '#8c8c8c'}} tickFormatter={(value) => `${value / 1000000}M`} />
                  <Tooltip 
                    formatter={(value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(value))}
                    contentStyle={{ borderRadius: 8, border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
                  />
                  <Area type="monotone" dataKey="revenue" stroke="#1890ff" strokeWidth={3} fillOpacity={1} fill="url(#colorRevenue)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card 
            bordered={false} 
            style={{ borderRadius: 12, boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}
            title={<span style={{ fontSize: 16, fontWeight: 700 }}>{t('admin.overview.bookingRatio')}</span>}
          >
            <div style={{ width: '100%', height: 300, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={5}
                    dataKey="value"
                    labelLine={false}
                    label={renderCustomizedLabel}
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ borderRadius: 8, border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                  <Legend verticalAlign="bottom" height={36}/>
                </PieChart>
              </ResponsiveContainer>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default AdminOverview;
