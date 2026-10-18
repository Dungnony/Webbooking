import React, { useEffect, useState } from 'react';
import { Table, Tag, Button, Empty, message, Modal, Form, Input, Select, Card } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { userApi } from '../../api';
import type { User } from '../../api-generated';

const AdminUsers: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form] = Form.useForm<User>();

  const loadUsers = () => {
    setLoading(true);
    userApi.getAll()
      .then(res => {
        setUsers(res.data || []);
      })
      .catch((err) => {
        message.error('Không thể lấy danh sách người dùng từ Server!');
        console.error(err);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleDelete = async (id: number) => {
    Modal.confirm({
      title: 'Xác nhận xóa',
      content: 'Bạn có chắc chắn muốn xóa người dùng này khỏi hệ thống không?',
      okText: 'Xóa',
      okType: 'danger',
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await userApi.delete(id);
          message.success('Đã xóa người dùng thành công!');
          loadUsers();
        } catch (error) {
          message.error('Không thể xóa người dùng.');
        }
      }
    });
  };

  const openEditModal = (user: User) => {
    setEditingUser(user);
    form.setFieldsValue(user);
    setIsModalOpen(true);
  };

  const openCreateModal = () => {
    setEditingUser(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (values: User) => {
    try {
      if (editingUser?.id !== undefined) {
        const updated = { ...editingUser, ...values };
        await userApi.update(editingUser.id, updated);
        message.success('Cập nhật người dùng thành công!');
      } else {
        await userApi.create(values);
        message.success('Thêm người dùng mới thành công!');
      }
      setIsModalOpen(false);
      loadUsers();
    } catch (error) {
      message.error('Thao tác thất bại. Vui lòng thử lại.');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Tên đăng nhập', dataIndex: 'username', key: 'username', render: (v: string) => <b style={{ color: '#1a1a1a' }}>{v}</b> },
    { title: 'Email', dataIndex: 'email', key: 'email' },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      key: 'role',
      render: (v: string) => (
        <Tag color={v === 'ADMIN' ? 'red' : 'blue'}>
          {v || 'USER'}
        </Tag>
      ),
    },
    {
      title: 'Hành động',
      key: 'action',
      width: 180,
      render: (_: unknown, record: User) => {
        const userId = record.id;
        if (record.username === 'admin' || userId === undefined) return null;
        return (
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)}>Sửa</Button>
            <Button danger size="small" icon={<DeleteOutlined />} onClick={() => handleDelete(userId)}>Xóa</Button>
          </div>
        );
      }
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>Danh sách người dùng</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          Thêm tài khoản
        </Button>
      </div>

      {loading ? (
        <div style={{ background: '#fff', borderRadius: 8, overflow: 'hidden', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
          {/* Table header skeleton */}
          <div style={{ display: 'flex', gap: 0, padding: '12px 16px', borderBottom: '1px solid #f0f0f0', background: '#fafafa' }}>
            {[80, 160, 200, 100, 120].map((w, i) => (
              <div key={i} style={{ width: w, height: 14, borderRadius: 4, marginRight: 24, background: 'linear-gradient(90deg, #e8e8e8 25%, #d8d8d8 50%, #e8e8e8 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
            ))}
          </div>
          {/* Table rows skeleton */}
          {[...Array(6)].map((_, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', padding: '16px', borderBottom: '1px solid #f5f5f5', gap: 0 }}>
              {[80, 160, 200, 100, 120].map((w, j) => (
                <div key={j} style={{ width: w, height: 14, borderRadius: 4, marginRight: 24, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: `shimmer 1.4s ease-in-out ${i * 0.1}s infinite` }} />
              ))}
            </div>
          ))}
        </div>
      ) : users.length === 0 ? (
        <Empty description="Không có tài khoản nào" />
      ) : (
        <Card style={{ padding: 0 }} bodyStyle={{ padding: 0 }}>
          <Table
            dataSource={users}
            columns={columns}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 800 }}
          />
        </Card>
      )}

      <Modal
        title={editingUser ? 'Chỉnh sửa tài khoản' : 'Thêm tài khoản mới'}
        open={isModalOpen}
        onCancel={() => setIsModalOpen(false)}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={handleModalSubmit} size="large" style={{ marginTop: 16 }}>
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
          >
            <Input disabled={!!editingUser} />
          </Form.Item>

          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Vui lòng nhập email!' },
              { type: 'email', message: 'Email không đúng định dạng!' }
            ]}
          >
            <Input />
          </Form.Item>

          {!editingUser && (
            <Form.Item
              name="password"
              label="Mật khẩu"
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu!' }]}
            >
              <Input.Password />
            </Form.Item>
          )}

          <Form.Item
            name="role"
            label="Vai trò (Role)"
            rules={[{ required: true, message: 'Vui lòng chọn vai trò!' }]}
            initialValue="USER"
          >
            <Select>
              <Select.Option value="USER">USER</Select.Option>
              <Select.Option value="ADMIN">ADMIN</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right', marginTop: 24 }}>
            <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>
              Hủy
            </Button>
            <Button type="primary" htmlType="submit">
              Xác nhận
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AdminUsers;
