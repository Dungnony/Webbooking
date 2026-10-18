import React, { useCallback, useEffect, useState } from 'react';
import { Table, Tag, Button, Spin, Empty, message, Modal, Input, Card, Select } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { adminApi, BookingRequestStatus } from '../../api';
import type { BookingRequest } from '../../api-generated';

const AdminBookingRequests: React.FC = () => {
  const [requests, setRequests] = useState<BookingRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterStatus, setFilterStatus] = useState<BookingRequestStatus | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [adminResponse, setAdminResponse] = useState('');
  const [currentRequestId, setCurrentRequestId] = useState<number | null>(null);

  const loadRequests = useCallback(() => {
    setLoading(true);
    adminApi.getBookingRequests(filterStatus)
      .then(res => {
        setRequests(res.data || []);
      })
      .catch((err) => {
        message.error('Không thể tải danh sách yêu cầu');
        console.error(err);
      })
      .finally(() => setLoading(false));
  }, [filterStatus]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const openModal = (id: number, type: 'APPROVE' | 'REJECT') => {
    setCurrentRequestId(id);
    setModalType(type);
    setAdminResponse('');
    setModalOpen(true);
  };

  const handleSubmitProcess = async () => {
    if (!currentRequestId) return;
    
    // Nếu từ chối, bắt buộc nhập lý do (có thể nới lỏng tùy yêu cầu, nhưng nên có)
    if (modalType === 'REJECT' && !adminResponse.trim()) {
      message.warning('Vui lòng nhập lý do từ chối');
      return;
    }

    try {
      if (modalType === 'APPROVE') {
        await adminApi.approveBookingRequest(currentRequestId, adminResponse);
        message.success('Đã duyệt yêu cầu thành công');
      } else {
        await adminApi.rejectBookingRequest(currentRequestId, adminResponse);
        message.success('Đã từ chối yêu cầu');
      }
      setModalOpen(false);
      loadRequests();
    } catch (error) {
      message.error('Xử lý yêu cầu thất bại');
    }
  };

  const getTypeTag = (type?: string) => {
    switch (type) {
      case 'CANCEL': return <Tag color="magenta">Hủy Booking</Tag>;
      case 'CHANGE_DATE': return <Tag color="cyan">Đổi ngày</Tag>;
      default: return <Tag>{type}</Tag>;
    }
  };

  const getStatusTag = (status?: string) => {
    switch (status) {
      case 'PENDING': return <Tag color="gold">Chờ xử lý</Tag>;
      case 'APPROVED': return <Tag color="green">Đã duyệt</Tag>;
      case 'REJECTED': return <Tag color="red">Từ chối</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { 
      title: 'Mã Booking', 
      key: 'booking',
      render: (_: unknown, record: BookingRequest) => <b>#{record.booking?.id || 'N/A'}</b>
    },
    { 
      title: 'Loại yêu cầu', 
      dataIndex: 'type', 
      key: 'type',
      render: (v: string) => getTypeTag(v)
    },
    { 
      title: 'Chi tiết yêu cầu', 
      key: 'details',
      render: (_: unknown, record: BookingRequest) => (
        <div style={{ fontSize: 13 }}>
          {record.type === 'CHANGE_DATE' && (
            <div style={{ marginBottom: 4, color: '#006ce4' }}>
              Ngày mới: {record.newCheckIn} ➜ {record.newCheckOut}
            </div>
          )}
          <div style={{ color: '#595959' }}>
            <i>Lý do:</i> {record.reason || 'Không có lý do'}
          </div>
        </div>
      )
    },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (v: string) => getStatusTag(v)
    },
    {
      title: 'Phản hồi (Admin)',
      dataIndex: 'adminResponse',
      key: 'adminResponse',
      render: (v: string) => <span style={{ fontSize: 12, color: '#8c8c8c' }}>{v || '—'}</span>
    },
    {
      title: 'Hành động',
      key: 'action',
      render: (_: unknown, record: BookingRequest) => {
        const requestId = record.id;
        if (record.status !== 'PENDING' || requestId === undefined) return null;
        return (
          <div style={{ display: 'flex', gap: 6 }}>
            <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => openModal(requestId, 'APPROVE')}>Duyệt</Button>
            <Button size="small" danger icon={<CloseOutlined />} onClick={() => openModal(requestId, 'REJECT')}>Từ chối</Button>
          </div>
        );
      }
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>Yêu cầu Booking</h2>
        <Select
          style={{ width: 150 }}
          placeholder="Lọc theo trạng thái"
          allowClear
          value={filterStatus}
          onChange={setFilterStatus}
        >
          <Select.Option value="PENDING">Chờ xử lý</Select.Option>
          <Select.Option value="APPROVED">Đã duyệt</Select.Option>
          <Select.Option value="REJECTED">Từ chối</Select.Option>
        </Select>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : requests.length === 0 ? (
        <Empty description="Không có yêu cầu nào" />
      ) : (
        <Card style={{ padding: 0 }} bodyStyle={{ padding: 0 }}>
          <Table
            dataSource={requests}
            columns={columns}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 900 }}
          />
        </Card>
      )}

      {/* Modal Duyệt / Từ chối */}
      <Modal
        title={modalType === 'APPROVE' ? 'Duyệt Yêu cầu' : 'Từ chối Yêu cầu'}
        open={modalOpen}
        onOk={handleSubmitProcess}
        onCancel={() => setModalOpen(false)}
        okText={modalType === 'APPROVE' ? 'Xác nhận Duyệt' : 'Xác nhận Từ chối'}
        okButtonProps={modalType === 'REJECT' ? { danger: true } : { type: 'primary' }}
      >
        <p>Phản hồi của bạn sẽ được gửi tới khách hàng (không bắt buộc nếu duyệt):</p>
        <Input.TextArea
          rows={4}
          value={adminResponse}
          onChange={e => setAdminResponse(e.target.value)}
          placeholder={modalType === 'REJECT' ? 'Ví dụ: Khách sạn đã hết phòng trống ngày bạn muốn đổi...' : 'Ví dụ: Yêu cầu của bạn đã được chấp thuận...'}
        />
      </Modal>
    </div>
  );
};

export default AdminBookingRequests;
