import React, { useCallback, useEffect, useState } from 'react';
import { Table, Tag, Button, Spin, Empty, message, Modal, Input, Card, Select } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { adminApi, BookingRequestStatus } from '../../api';
import { getErrorMessage } from '../../utils/errors';
import type { BookingRequest } from '../../api-generated';

const AdminBookingRequests: React.FC = () => {
  const { t } = useTranslation();
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
        message.error(t('admin.requests.loadFailed'));
        console.error(err);
      })
      .finally(() => setLoading(false));
  }, [filterStatus, t]);

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
      message.warning(t('admin.common.reasonRequired'));
      return;
    }

    try {
      if (modalType === 'APPROVE') {
        await adminApi.approveBookingRequest(currentRequestId, adminResponse);
        message.success(t('admin.requests.approved'));
      } else {
        await adminApi.rejectBookingRequest(currentRequestId, adminResponse);
        message.success(t('admin.requests.rejected'));
      }
      setModalOpen(false);
      loadRequests();
    } catch (error) {
      message.error(getErrorMessage(error, t('admin.requests.processFailed')));
    }
  };

  const getTypeTag = (type?: string) => {
    switch (type) {
      case 'CANCEL': return <Tag color="magenta">{t('admin.requests.typeCancel')}</Tag>;
      case 'CHANGE_DATE': return <Tag color="cyan">{t('admin.requests.typeChangeDate')}</Tag>;
      default: return <Tag>{type}</Tag>;
    }
  };

  const getStatusTag = (status?: string) => {
    switch (status) {
      case 'PENDING': return <Tag color="gold">{t('admin.requests.statusPending')}</Tag>;
      case 'APPROVED': return <Tag color="green">{t('admin.status.confirmed')}</Tag>;
      case 'REJECTED': return <Tag color="red">{t('admin.status.rejected')}</Tag>;
      default: return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { 
      title: t('admin.requests.bookingCode'),
      key: 'booking',
      render: (_: unknown, record: BookingRequest) => <b>#{record.booking?.id || 'N/A'}</b>
    },
    { 
      title: t('admin.requests.type'),
      dataIndex: 'type', 
      key: 'type',
      render: (v: string) => getTypeTag(v)
    },
    { 
      title: t('admin.requests.details'),
      key: 'details',
      render: (_: unknown, record: BookingRequest) => (
        <div style={{ fontSize: 13 }}>
          {record.type === 'CHANGE_DATE' && (
            <div style={{ marginBottom: 4, color: '#006ce4' }}>
              {t('admin.requests.newDates')}: {record.newCheckIn} ➜ {record.newCheckOut}
            </div>
          )}
          <div style={{ color: '#595959' }}>
            <i>{t('admin.requests.reason')}:</i> {record.reason || t('admin.requests.noReason')}
          </div>
        </div>
      )
    },
    { 
      title: t('admin.common.status'),
      dataIndex: 'status', 
      key: 'status',
      render: (v: string) => getStatusTag(v)
    },
    {
      title: t('admin.requests.adminResponse'),
      dataIndex: 'adminResponse',
      key: 'adminResponse',
      render: (v: string) => <span style={{ fontSize: 12, color: '#8c8c8c' }}>{v || '—'}</span>
    },
    {
      title: t('admin.common.actions'),
      key: 'action',
      render: (_: unknown, record: BookingRequest) => {
        const requestId = record.id;
        if (record.status !== 'PENDING' || requestId === undefined) return null;
        return (
          <div style={{ display: 'flex', gap: 6 }}>
            <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => openModal(requestId, 'APPROVE')}>{t('admin.common.approve')}</Button>
            <Button size="small" danger icon={<CloseOutlined />} onClick={() => openModal(requestId, 'REJECT')}>{t('admin.common.reject')}</Button>
          </div>
        );
      }
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>{t('admin.menu.requests')}</h2>
        <Select
          style={{ width: 150 }}
          placeholder={t('admin.common.filterByStatus')}
          allowClear
          value={filterStatus}
          onChange={setFilterStatus}
        >
          <Select.Option value="PENDING">{t('admin.requests.statusPending')}</Select.Option>
          <Select.Option value="APPROVED">{t('admin.status.confirmed')}</Select.Option>
          <Select.Option value="REJECTED">{t('admin.status.rejected')}</Select.Option>
        </Select>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : requests.length === 0 ? (
        <Empty description={t('admin.requests.empty')} />
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
        title={modalType === 'APPROVE' ? t('admin.requests.approveTitle') : t('admin.requests.rejectTitle')}
        open={modalOpen}
        onOk={handleSubmitProcess}
        onCancel={() => setModalOpen(false)}
        okText={modalType === 'APPROVE' ? t('admin.common.confirmApprove') : t('admin.common.confirmReject')}
        okButtonProps={modalType === 'REJECT' ? { danger: true } : { type: 'primary' }}
      >
        <p>{t('admin.requests.responsePrompt')}</p>
        <Input.TextArea
          rows={4}
          value={adminResponse}
          onChange={e => setAdminResponse(e.target.value)}
          placeholder={modalType === 'REJECT' ? t('admin.requests.rejectPlaceholder') : t('admin.requests.approvePlaceholder')}
        />
      </Modal>
    </div>
  );
};

export default AdminBookingRequests;
