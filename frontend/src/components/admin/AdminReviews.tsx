import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Modal, Input, Tag, Switch, Space, message, Typography } from 'antd';
import { MessageOutlined, DeleteOutlined } from '@ant-design/icons';
import { adminApi, ReviewItem } from '../../api';
import { getErrorMessage } from '../../utils/errors';

const { TextArea } = Input;
const { Paragraph } = Typography;

const PAGE_SIZE = 20;
// Must match ReviewService.MAX_REPLY_LENGTH on the backend.
const MAX_REPLY_LENGTH = 2000;

const AdminReviews: React.FC = () => {
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [onlyUnanswered, setOnlyUnanswered] = useState(true);
  const [loading, setLoading] = useState(false);

  const [replyingTo, setReplyingTo] = useState<ReviewItem | null>(null);
  const [replyText, setReplyText] = useState('');
  const [saving, setSaving] = useState(false);

  const loadReviews = useCallback(async () => {
    setLoading(true);
    try {
      const data = await adminApi.getReviews(page, PAGE_SIZE, onlyUnanswered);
      setReviews(data.content);
      setTotal(data.totalElements);
    } catch (err) {
      message.error(getErrorMessage(err, 'Không thể tải danh sách đánh giá'));
    } finally {
      setLoading(false);
    }
  }, [page, onlyUnanswered]);

  useEffect(() => { loadReviews(); }, [loadReviews]);

  const openReply = (review: ReviewItem) => {
    setReplyingTo(review);
    setReplyText(review.hotelReply ?? '');
  };

  const handleSaveReply = async () => {
    if (!replyingTo) return;
    if (!replyText.trim()) {
      message.warning('Vui lòng nhập nội dung phản hồi');
      return;
    }
    setSaving(true);
    try {
      await adminApi.replyReview(replyingTo.id, replyText.trim());
      message.success('Đã gửi phản hồi');
      setReplyingTo(null);
      loadReviews();
    } catch (err) {
      message.error(getErrorMessage(err, 'Gửi phản hồi thất bại'));
    } finally {
      setSaving(false);
    }
  };

  const handleRemoveReply = (review: ReviewItem) => {
    Modal.confirm({
      title: 'Xóa phản hồi?',
      content: 'Phản hồi của khách sạn cho đánh giá này sẽ bị xóa.',
      okText: 'Xóa',
      okType: 'danger',
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await adminApi.removeReviewReply(review.id);
          message.success('Đã xóa phản hồi');
          loadReviews();
        } catch (err) {
          message.error(getErrorMessage(err, 'Xóa phản hồi thất bại'));
        }
      },
    });
  };

  const columns = [
    { title: 'Khách sạn', dataIndex: 'hotelName', key: 'hotelName', width: 180, render: (name?: string) => <b>{name || '—'}</b> },
    { title: 'Khách', dataIndex: 'username', key: 'username', width: 120 },
    { title: 'Điểm', dataIndex: 'rating', key: 'rating', width: 70, render: (r: number) => <Tag color={r >= 8 ? 'green' : r >= 5 ? 'gold' : 'red'}>{r}</Tag> },
    {
      title: 'Nội dung',
      key: 'content',
      render: (_: unknown, review: ReviewItem) => (
        <div>
          <Paragraph ellipsis={{ rows: 3, expandable: true, symbol: 'Xem thêm' }} style={{ marginBottom: 4 }}>
            {review.comment || <i style={{ color: '#999' }}>(không có nhận xét)</i>}
          </Paragraph>
          {review.hotelReply && (
            <div style={{ background: '#f0f5ff', borderLeft: '3px solid #006ce4', padding: '6px 10px', fontSize: 13 }}>
              <b>Phản hồi:</b> {review.hotelReply}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Ngày',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 110,
      render: (d?: string) => (d ? new Date(d).toLocaleDateString('vi-VN') : '—'),
    },
    {
      title: 'Hành động',
      key: 'action',
      width: 150,
      render: (_: unknown, review: ReviewItem) => (
        <Space>
          <Button size="small" type="primary" icon={<MessageOutlined />} onClick={() => openReply(review)}>
            {review.hotelReply ? 'Sửa' : 'Trả lời'}
          </Button>
          {review.hotelReply && (
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleRemoveReply(review)} />
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>Đánh giá của khách</h2>
        <Space>
          <span>Chỉ hiện đánh giá chưa trả lời</span>
          <Switch
            checked={onlyUnanswered}
            onChange={checked => { setOnlyUnanswered(checked); setPage(0); }}
          />
        </Space>
      </div>

      <Table
        dataSource={reviews}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total,
          onChange: p => setPage(p - 1),
          showSizeChanger: false,
        }}
      />

      <Modal
        title={`Phản hồi đánh giá của ${replyingTo?.username ?? ''}`}
        open={replyingTo !== null}
        onOk={handleSaveReply}
        onCancel={() => setReplyingTo(null)}
        confirmLoading={saving}
        okText="Gửi phản hồi"
        cancelText="Hủy"
      >
        {replyingTo && (
          <div style={{ background: '#fafafa', padding: 12, borderRadius: 6, marginBottom: 12 }}>
            <Tag>{replyingTo.rating}/10</Tag> {replyingTo.comment}
          </div>
        )}
        <TextArea
          rows={4}
          value={replyText}
          maxLength={MAX_REPLY_LENGTH}
          showCount
          onChange={e => setReplyText(e.target.value)}
          placeholder="Phản hồi sẽ hiển thị công khai dưới đánh giá, đứng tên khách sạn."
        />
      </Modal>
    </div>
  );
};

export default AdminReviews;
