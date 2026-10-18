import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, message, Upload, Drawer, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, PictureOutlined } from '@ant-design/icons';
import { hotelApi, imageApi, roomApi, HotelWithStats } from '../../api';
import type { Hotel, Room } from '../../api-generated';
import type { UploadFile } from 'antd/es/upload/interface';
import { cachedFetch, invalidateCache, HOTEL_LIST_TTL } from '../../utils/apiCache';
import { getHotelImage } from '../../utils/imageUtils';
import { getErrorMessage } from '../../utils/errors';

const { TextArea } = Input;

type HotelFormValues = Pick<Hotel, 'name' | 'city' | 'address' | 'description'>;
type RoomFormValues = Pick<Room, 'name' | 'type' | 'pricePerNight' | 'capacity' | 'quantity' | 'description'>;

const AdminHotels: React.FC = () => {
  const [hotels, setHotels] = useState<HotelWithStats[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal Khách sạn
  const [hotelModalOpen, setHotelModalOpen] = useState(false);
  const [editingHotel, setEditingHotel] = useState<Hotel | null>(null);
  const [hotelForm] = Form.useForm<HotelFormValues>();

  // Upload Ảnh Khách sạn
  const [uploadHotelModalOpen, setUploadHotelModalOpen] = useState(false);
  const [currentUploadHotelId, setCurrentUploadHotelId] = useState<number | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);

  // Drawer Phòng
  const [roomsDrawerOpen, setRoomsDrawerOpen] = useState(false);
  const [currentHotelForRooms, setCurrentHotelForRooms] = useState<Hotel | null>(null);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loadingRooms, setLoadingRooms] = useState(false);

  // Modal Thêm Phòng
  const [roomModalOpen, setRoomModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<Room | null>(null);
  const [roomForm] = Form.useForm<RoomFormValues>();

  // Upload Ảnh Phòng
  const [uploadRoomModalOpen, setUploadRoomModalOpen] = useState(false);
  const [currentUploadRoomId, setCurrentUploadRoomId] = useState<number | null>(null);
  const [roomFileList, setRoomFileList] = useState<UploadFile[]>([]);

  useEffect(() => {
    loadHotels();
  }, []);

  const loadHotels = async () => {
    try {
      setLoading(true);
      const data = await cachedFetch('hotels_all', () => hotelApi.getAll(), HOTEL_LIST_TTL);
      setHotels(data || []);
    } catch (error) {
      message.error(getErrorMessage(error, 'Không thể tải danh sách khách sạn'));
    } finally {
      setLoading(false);
    }
  };

  const handleSaveHotel = async (values: HotelFormValues) => {
    try {
      if (editingHotel?.id !== undefined) {
        // PUT replaces the whole entity, so keep fields the form does not edit (image, rating, status).
        // Rooms are owned by the Room side and must not be sent back (undefined is dropped from the JSON).
        await hotelApi.update(editingHotel.id, { ...editingHotel, ...values, rooms: undefined });
        invalidateCache(`hotel_${editingHotel.id}`);
        message.success('Cập nhật khách sạn thành công');
      } else {
        await hotelApi.create(values);
        message.success('Thêm khách sạn thành công');
      }
      setHotelModalOpen(false);
      // Xóa cache để force reload dữ liệu mới
      invalidateCache('hotels_all');
      loadHotels();
    } catch (error) {
      message.error(getErrorMessage(error, 'Lưu khách sạn thất bại'));
    }
  };

  const handleDeleteHotel = (id: number) => {
    Modal.confirm({
      title: 'Xóa khách sạn',
      content: 'Bạn có chắc chắn muốn xóa khách sạn này không? Hành động này không thể hoàn tác.',
      okText: 'Xóa',
      okType: 'danger',
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await hotelApi.delete(id);
          message.success('Đã xóa khách sạn');
          // Xóa cache sau khi xóa KS
          invalidateCache('hotels_all');
          invalidateCache(`hotel_${id}`);
          loadHotels();
        } catch (error) {
          message.error(getErrorMessage(error, 'Lỗi khi xóa khách sạn'));
        }
      }
    });
  };

  const handleUploadHotelImage = async () => {
    if (!currentUploadHotelId || fileList.length === 0) {
      message.warning('Vui lòng chọn ảnh');
      return;
    }
    const file = fileList[0].originFileObj as File;
    try {
      setUploading(true);
      await imageApi.uploadHotelImage(currentUploadHotelId, file);
      message.success('Tải ảnh lên thành công');
      setUploadHotelModalOpen(false);
      setFileList([]);
      invalidateCache('hotels_all');
      invalidateCache(`hotel_${currentUploadHotelId}`);
      loadHotels();
    } catch (error) {
      message.error(getErrorMessage(error, 'Lỗi khi tải ảnh lên'));
    } finally {
      setUploading(false);
    }
  };

  const openRoomsDrawer = async (hotel: Hotel) => {
    if (hotel.id === undefined) return;
    setCurrentHotelForRooms(hotel);
    setRoomsDrawerOpen(true);
    loadRooms(hotel.id);
  };

  const loadRooms = async (hotelId: number) => {
    try {
      setLoadingRooms(true);
      const res = await roomApi.getByHotel(hotelId);
      setRooms(res.data || []);
    } catch (error) {
      message.error(getErrorMessage(error, 'Không thể tải danh sách phòng'));
    } finally {
      setLoadingRooms(false);
    }
  };

  const reloadCurrentHotelRooms = () => {
    if (currentHotelForRooms?.id !== undefined) {
      loadRooms(currentHotelForRooms.id);
    }
  };

  const openRoomModal = (room: Room | null) => {
    setEditingRoom(room);
    roomForm.resetFields();
    roomForm.setFieldsValue(room ?? { capacity: 2, quantity: 1 });
    setRoomModalOpen(true);
  };

  const handleSaveRoom = async (values: RoomFormValues) => {
    if (currentHotelForRooms?.id === undefined) return;
    try {
      if (editingRoom?.id !== undefined) {
        await roomApi.update(editingRoom.id, values);
        message.success('Cập nhật phòng thành công');
      } else {
        await roomApi.create(currentHotelForRooms.id, values);
        message.success('Thêm phòng thành công');
      }
      setRoomModalOpen(false);
      invalidateCache('hotels_all');
      invalidateCache(`hotel_${currentHotelForRooms.id}`);
      reloadCurrentHotelRooms();
    } catch (error) {
      message.error(getErrorMessage(error, editingRoom ? 'Lỗi khi cập nhật phòng' : 'Lỗi khi thêm phòng'));
    }
  };

  const handleDeleteRoom = async (roomId: number) => {
    Modal.confirm({
      title: 'Xóa phòng',
      content: 'Bạn có chắc muốn xóa phòng này?',
      okText: 'Xóa',
      okType: 'danger',
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await roomApi.delete(roomId);
          message.success('Đã xóa phòng');
          reloadCurrentHotelRooms();
        } catch (error) {
          message.error(getErrorMessage(error, 'Lỗi khi xóa phòng'));
        }
      }
    });
  };

  const handleUploadRoomImage = async () => {
    if (!currentUploadRoomId || roomFileList.length === 0) {
      message.warning('Vui lòng chọn ảnh');
      return;
    }
    const file = roomFileList[0].originFileObj as File;
    try {
      setUploading(true);
      await imageApi.uploadRoomImage(currentUploadRoomId, file);
      message.success('Tải ảnh lên thành công');
      setUploadRoomModalOpen(false);
      setRoomFileList([]);
      reloadCurrentHotelRooms();
    } catch (error) {
      message.error(getErrorMessage(error, 'Lỗi khi tải ảnh lên'));
    } finally {
      setUploading(false);
    }
  };

  const hotelColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    {
      title: 'Ảnh đại diện',
      key: 'image',
      render: (_: unknown, record: HotelWithStats) => {
        const imageUrl = getHotelImage(record);
        return imageUrl ? (
          <img src={imageUrl} alt="hotel" referrerPolicy="no-referrer" style={{ width: 60, height: 40, objectFit: 'cover', borderRadius: 4 }} />
        ) : (
          <div style={{ width: 60, height: 40, background: '#f0f0f0', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <PictureOutlined style={{ color: '#ccc' }} />
          </div>
        );
      }
    },
    { title: 'Tên khách sạn', dataIndex: 'name', key: 'name', render: (text: string) => <b>{text}</b> },
    { title: 'Thành phố', dataIndex: 'city', key: 'city' },
    { title: 'Đánh giá', dataIndex: 'ratingAvg', key: 'ratingAvg', render: (r?: number) => r ? <Tag color="gold">{r.toFixed(1)}</Tag> : '—' },
    {
      title: 'Hành động',
      key: 'action',
      render: (_: unknown, record: HotelWithStats) => {
        const hotelId = record.id;
        if (hotelId === undefined) return null;
        return (
          <Space>
            <Button size="small" type="primary" onClick={() => openRoomsDrawer(record)}>
              Quản lý Phòng
            </Button>
            <Button size="small" icon={<UploadOutlined />} onClick={() => {
              setCurrentUploadHotelId(hotelId);
              setFileList([]);
              setUploadHotelModalOpen(true);
            }}>
              Ảnh
            </Button>
            <Button size="small" icon={<EditOutlined />} onClick={() => {
              setEditingHotel(record);
              hotelForm.setFieldsValue(record);
              setHotelModalOpen(true);
            }} />
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteHotel(hotelId)} />
          </Space>
        );
      }
    }
  ];

  const roomColumns = [
    { title: 'Tên phòng', dataIndex: 'name', key: 'name', render: (t: string) => <b>{t}</b> },
    { title: 'Loại', dataIndex: 'type', key: 'type' },
    { title: 'Giá / đêm', dataIndex: 'pricePerNight', key: 'pricePerNight', render: (p?: number) => p ? `${p.toLocaleString('vi-VN')}₫` : '—' },
    { title: 'Sức chứa', dataIndex: 'capacity', key: 'capacity' },
    { title: 'Số lượng', dataIndex: 'quantity', key: 'quantity' },
    {
      title: 'Ảnh',
      key: 'image',
      render: (_: unknown, record: Room) => {
        const roomId = record.id;
        if (roomId === undefined) return null;
        return (
          <Button size="small" icon={<UploadOutlined />} onClick={() => {
            setCurrentUploadRoomId(roomId);
            setRoomFileList([]);
            setUploadRoomModalOpen(true);
          }}>
            Upload
          </Button>
        );
      }
    },
    {
      title: 'Hành động',
      key: 'action',
      render: (_: unknown, record: Room) => {
        const roomId = record.id;
        if (roomId === undefined) return null;
        return (
          <Space>
            <Button size="small" icon={<EditOutlined />} onClick={() => openRoomModal(record)} />
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteRoom(roomId)} />
          </Space>
        );
      }
    }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>Quản lý Khách sạn</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => {
          setEditingHotel(null);
          hotelForm.resetFields();
          setHotelModalOpen(true);
        }}>
          Thêm khách sạn
        </Button>
      </div>

      <Table
        dataSource={hotels}
        columns={hotelColumns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
      />

      {/* Modal Thêm/Sửa Khách sạn */}
      <Modal
        title={editingHotel ? "Sửa thông tin khách sạn" : "Thêm khách sạn mới"}
        open={hotelModalOpen}
        onOk={() => hotelForm.submit()}
        onCancel={() => setHotelModalOpen(false)}
        width={700}
      >
        <Form form={hotelForm} layout="vertical" onFinish={handleSaveHotel}>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item name="name" label="Tên khách sạn" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input placeholder="Ví dụ: InterContinental" />
            </Form.Item>
            <Form.Item name="city" label="Thành phố" rules={[{ required: true }]} style={{ width: 150 }}>
              <Input placeholder="Hà Nội" />
            </Form.Item>
          </div>
          <Form.Item name="address" label="Địa chỉ cụ thể" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Mô tả" rules={[{ required: true }]}>
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal Upload Ảnh Khách sạn */}
      <Modal
        title="Tải ảnh Khách sạn lên"
        open={uploadHotelModalOpen}
        onOk={handleUploadHotelImage}
        onCancel={() => setUploadHotelModalOpen(false)}
        confirmLoading={uploading}
      >
        <Upload
          beforeUpload={() => false}
          fileList={fileList}
          onChange={({ fileList }) => setFileList(fileList.slice(-1))}
          listType="picture"
          accept=".jpg,.jpeg,.png,.webp,.gif"
        >
          <Button icon={<UploadOutlined />}>Chọn ảnh</Button>
        </Upload>
      </Modal>

      {/* Drawer Quản lý Phòng */}
      <Drawer
        title={`Quản lý phòng: ${currentHotelForRooms?.name}`}
        placement="right"
        width={800}
        onClose={() => setRoomsDrawerOpen(false)}
        open={roomsDrawerOpen}
      >
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openRoomModal(null)}>
            Thêm phòng
          </Button>
        </div>
        <Table
          dataSource={rooms}
          columns={roomColumns}
          rowKey="id"
          loading={loadingRooms}
          pagination={false}
        />
      </Drawer>

      {/* Modal Thêm/Sửa Phòng */}
      <Modal
        title={editingRoom ? 'Sửa thông tin phòng' : 'Thêm phòng mới'}
        open={roomModalOpen}
        onOk={() => roomForm.submit()}
        onCancel={() => setRoomModalOpen(false)}
      >
        <Form form={roomForm} layout="vertical" onFinish={handleSaveRoom}>
          <Form.Item name="name" label="Tên phòng" rules={[{ required: true }]}>
            <Input placeholder="P.101" />
          </Form.Item>
          <Form.Item name="type" label="Loại phòng" rules={[{ required: true }]}>
            <Input placeholder="Deluxe, Standard..." />
          </Form.Item>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item name="pricePerNight" label="Giá mỗi đêm (VNĐ)" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Input type="number" min={1} />
            </Form.Item>
            <Form.Item name="capacity" label="Sức chứa" rules={[{ required: true }]} style={{ width: 100 }}>
              <Input type="number" min={1} />
            </Form.Item>
            <Form.Item
              name="quantity"
              label="Số lượng"
              rules={[{ required: true }]}
              tooltip="Đặt 0 để ngừng nhận đặt phòng mà vẫn giữ lịch sử"
              style={{ width: 100 }}
            >
              <Input type="number" min={0} />
            </Form.Item>
          </div>
          <Form.Item name="description" label="Mô tả phòng">
            <TextArea rows={2} placeholder="Điều hòa, Tủ lạnh..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* Modal Upload Ảnh Phòng */}
      <Modal
        title="Tải ảnh Phòng lên"
        open={uploadRoomModalOpen}
        onOk={handleUploadRoomImage}
        onCancel={() => setUploadRoomModalOpen(false)}
        confirmLoading={uploading}
      >
        <Upload
          beforeUpload={() => false}
          fileList={roomFileList}
          onChange={({ fileList }) => setRoomFileList(fileList.slice(-1))}
          listType="picture"
          accept=".jpg,.jpeg,.png,.webp,.gif"
        >
          <Button icon={<UploadOutlined />}>Chọn ảnh</Button>
        </Upload>
      </Modal>

    </div>
  );
};

export default AdminHotels;
