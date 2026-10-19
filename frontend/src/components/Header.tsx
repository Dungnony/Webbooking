import React, { useRef, useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Popover, Badge, List, Typography, Spin, Tooltip, Modal, message } from 'antd';
import { BellOutlined, DeleteOutlined, SyncOutlined, CheckOutlined, DownOutlined, HeartOutlined, CarryOutOutlined } from '@ant-design/icons';
import { useWishlist } from '../context/useWishlist';
import { notificationApi, userApi } from '../api';
import type { Notification } from '../api-generated';
import { cachedFetch, invalidateCache, PROFILE_CACHE_KEY } from '../utils/apiCache';
import { clearSession } from '../utils/auth';
import { getErrorMessage } from '../utils/errors';
import { useTranslation } from 'react-i18next';
import { dateLocale } from '../i18n';
import LanguageSwitcher from './LanguageSwitcher';

const { Text } = Typography;

const NOTIFICATION_POLL_INTERVAL_MS = 60_000;
const PROFILE_CACHE_TTL_MS = 5 * 60_000;

interface HeaderProps {
  fullWidth?: boolean;
}

const Header: React.FC<HeaderProps> = ({ fullWidth }) => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const token = localStorage.getItem('token');
  const username = localStorage.getItem('username');
  const isAdmin = localStorage.getItem('role') === 'ADMIN';
  const { wishlist } = useWishlist();

  const [avatarUrl, setAvatarUrl] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  // The header is on every page, so the profile is cached instead of refetched on each navigation.
  useEffect(() => {
    if (!token) return;
    cachedFetch(PROFILE_CACHE_KEY, () => userApi.getMe().then(data => ({ data })), PROFILE_CACHE_TTL_MS)
      .then(profile => setAvatarUrl(profile.avatarUrl || ''))
      .catch(err => console.error('Failed to load profile', err));
  }, [token]);

  // State thông báo
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loadingNotifications, setLoadingNotifications] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);

  // State modal chi tiết thông báo
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);
  const [isModalVisible, setIsModalVisible] = useState(false);

  const fetchNotifications = useCallback(async () => {
    if (!token) return;
    setLoadingNotifications(true);
    try {
      const res = await notificationApi.getMy();
      setNotifications(res.data || []);
    } catch (err) {
      console.error('Failed to fetch notifications', err);
    } finally {
      setLoadingNotifications(false);
    }
  }, [token]);

  useEffect(() => {
    if (token) {
      fetchNotifications();
      const intervalId = setInterval(fetchNotifications, NOTIFICATION_POLL_INTERVAL_MS);
      return () => clearInterval(intervalId);
    }
  }, [token, fetchNotifications]);

  const unreadCount = notifications.filter(n => !n.read).length;

  const handleReadNotification = async (notification: Notification) => {
    if (notification.read || notification.id === undefined) return;
    try {
      await notificationApi.markAsRead(notification.id);
      setNotifications(prev =>
        prev.map(n => n.id === notification.id ? { ...n, read: true } : n)
      );
    } catch (error) {
      console.error('Lỗi khi đánh dấu thông báo đã đọc', error);
    }
  };

  const handleNotificationClick = (item: Notification) => {
    handleReadNotification(item);
    setSelectedNotification(item);
    setIsModalVisible(true);
    setShowNotifications(false); // Ẩn menu dropdown khi mở popup
  };

  const handleDeleteNotification = async (e: React.MouseEvent, id: number | undefined) => {
    e.stopPropagation(); // Ngăn sự kiện click lan ra ngoài (không trigger handleReadNotification)
    if (id === undefined) return;
    try {
      await notificationApi.delete(id);
      setNotifications(prev => prev.filter(n => n.id !== id));
    } catch (error) {
      console.error('Lỗi khi xóa thông báo', error);
    }
  };

  const handleLogout = () => {
    clearSession();
    navigate('/login');
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const profile = await userApi.uploadAvatar(file);
      setAvatarUrl(profile.avatarUrl || '');
      invalidateCache(PROFILE_CACHE_KEY);
      message.success(t('header.avatarUpdated'));
    } catch (err) {
      message.error(getErrorMessage(err, t('header.avatarFailed')));
    }
  };

  const timeAgo = (dateString: string) => {
    const diff = Date.now() - new Date(dateString).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor(diff / (1000 * 60));

    if (days === 1) return t('common.timeAgo.oneDay');
    if (days > 1) return t('common.timeAgo.days', { count: days });
    if (hours > 0) return t('common.timeAgo.hours', { count: hours });
    if (minutes > 0) return t('common.timeAgo.minutes', { count: minutes });
    return t('common.timeAgo.justNow');
  };

  const notificationContent = (
    <div style={{ width: 360, maxHeight: 500, overflowY: 'auto', padding: 0 }}>
      <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <b style={{ fontSize: 15, color: '#333' }}>{t('header.myNotifications', { count: notifications.length })}</b>
        <div style={{ display: 'flex', gap: 12, fontSize: 18, color: '#003b95' }}>
          <SyncOutlined style={{ cursor: 'pointer' }} onClick={fetchNotifications} spin={loadingNotifications} />
          <CheckOutlined 
            style={{ cursor: 'pointer' }} 
            onClick={async () => {
              for (const n of notifications.filter(x => !x.read)) {
                await handleReadNotification(n);
              }
            }} 
          />
        </div>
      </div>
      {loadingNotifications && notifications.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 20 }}><Spin /></div>
      ) : notifications.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 20, color: '#888' }}>{t('header.noNotifications')}</div>
      ) : (
        <List
          dataSource={notifications}
          renderItem={(item) => (
            <List.Item 
              style={{ 
                cursor: 'pointer', 
                background: item.read ? '#fff' : '#f4f9fd',
                padding: '16px 16px',
                borderBottom: '1px solid #e8e8e8',
                position: 'relative'
              }}
              onClick={() => handleNotificationClick(item)}
            >
                <div style={{ display: 'flex', flexDirection: 'column', width: '100%', paddingRight: 28 }}>
                  <Text strong style={{ color: '#c00000', fontSize: 14, lineHeight: 1.4 }}>{item.title}</Text>
                  
                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                    <Text type="secondary" style={{ fontSize: 12, fontStyle: 'italic', color: '#b3b3b3' }}>
                      {item.createdAt ? timeAgo(item.createdAt) : ''}
                    </Text>
                  </div>
                </div>
                <DeleteOutlined 
                  onClick={(e) => handleDeleteNotification(e, item.id)}
                  style={{ 
                    position: 'absolute', 
                    top: 16, 
                    right: 16, 
                    color: '#999', 
                    fontSize: 16,
                    padding: 4,
                    cursor: 'pointer'
                  }} 
                  title={t('header.deleteNotification')}
                />
              </List.Item>
          )}
        />
      )}
      {notifications.length > 0 && (
        <div style={{ padding: '12px', textAlign: 'center', color: '#c00000', cursor: 'pointer', fontSize: 14 }}>
          <DownOutlined style={{ marginRight: 4 }} /> {t('header.loadMore')}
        </div>
      )}
    </div>
  );

  const popoverContent = (
    <div style={{ minWidth: 200 }}>
      {/* Avatar upload inside popover */}
      <div className="bk-popover-avatar-section">
        <div
          className="bk-popover-avatar"
          onClick={() => fileInputRef.current?.click()}
          title={t('header.changeAvatar')}
        >
          {avatarUrl ? (
            <img src={avatarUrl} alt="avatar" className="bk-popover-avatar-img" />
          ) : (
            <span className="bk-popover-avatar-initial">
              {username?.charAt(0).toUpperCase() || 'U'}
            </span>
          )}
          <div className="bk-popover-avatar-overlay">📷</div>
        </div>
        <div className="bk-popover-username">{username}</div>
        <input
          ref={fileInputRef}
          type="file"
          accept=".jpg,.jpeg,.png,.webp,.gif"
          style={{ display: 'none' }}
          onChange={handleAvatarChange}
        />
      </div>
      <div style={{ borderTop: '1px solid #e7e7e7' }} />
      <div className="bk-popover-item" onClick={() => navigate('/profile')}>{t('header.myProfile')}</div>

      {/* Hiện menu Admin trên mobile */}
      {isAdmin && (
        <div className="bk-popover-item mobile-only-flex" onClick={() => navigate('/admin')} style={{ fontWeight: 700, color: '#006ce4' }}>
          {t('header.adminDashboard')}
        </div>
      )}

      <div className="bk-popover-item bk-popover-item-danger" onClick={handleLogout}>
        {t('header.logout')}
      </div>
    </div>
  );

  return (
    <header className="bk-header">
      {/* Top row */}
      <div className="bk-header-top">
        <div className="bk-header-top-inner" style={fullWidth ? { maxWidth: '100%', padding: '0 24px' } : undefined}>
          {/* Logo */}
          <div className="bk-logo" onClick={() => navigate(isAdmin ? '/admin' : '/')}>
            Booking<span>.com</span>
          </div>

          {/* Right actions */}
          <div className="bk-header-actions">
            <LanguageSwitcher />
            {token ? (
              <>
                {/* Ẩn bớt các nút của User nếu là Admin */}
                {!isAdmin && (
                  <>
                    <Tooltip title={t('header.wishlist')}>
                      <button className="bk-header-action-btn" onClick={() => navigate('/wishlist')} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Badge count={wishlist.length} size="small" offset={[4, -2]}>
                          <HeartOutlined style={{ color: '#fff', fontSize: 20 }} />
                        </Badge>
                      </button>
                    </Tooltip>

                    <Tooltip title={t('header.myBookings')}>
                      <button className="bk-header-action-btn" onClick={() => navigate('/my-bookings')} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <CarryOutOutlined style={{ color: '#fff', fontSize: 21 }} />
                      </button>
                    </Tooltip>
                  </>
                )}

                <Popover
                  content={notificationContent}
                  trigger="click"
                  placement="bottomRight"
                  open={showNotifications}
                  onOpenChange={setShowNotifications}
                  overlayInnerStyle={{ borderRadius: 12, padding: 8 }}
                >
                  <button className="bk-header-action-btn" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Badge count={unreadCount} size="small" offset={[4, -2]}>
                      <BellOutlined style={{ color: '#fff', fontSize: 20 }} />
                    </Badge>
                  </button>
                </Popover>

                <Popover
                  content={popoverContent}
                  trigger="click"
                  placement="bottomRight"
                  overlayInnerStyle={{ padding: 0, borderRadius: 8, overflow: 'hidden' }}
                >
                  <button className="bk-header-user-btn">
                    <div className="bk-avatar">
                      {avatarUrl ? (
                        <img src={avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
                      ) : (
                        username?.charAt(0).toUpperCase() || 'U'
                      )}
                    </div>
                    <div className="bk-avatar-name hide-on-mobile">{username}</div>
                  </button>
                </Popover>
              </>
            ) : (
              <>
                <button className="bk-header-action-btn" onClick={() => navigate('/register')}>
                  {t('header.register')}
                </button>
                <button className="bk-header-signin-btn" onClick={() => navigate('/login')}>
                  {t('header.signIn')}
                </button>
              </>
            )}
          </div>
        </div>
      </div>
      
      {/* Modal chi tiết thông báo */}
      <Modal
        title={<span style={{ fontSize: 18, color: '#003b95' }}>{selectedNotification?.title}</span>}
        open={isModalVisible}
        onCancel={() => setIsModalVisible(false)}
        footer={null}
        centered
        width={500}
      >
        <div style={{ padding: '10px 0' }}>
          <p style={{ fontSize: 15, lineHeight: 1.6, color: '#333', whiteSpace: 'pre-wrap' }}>
            {selectedNotification?.message}
          </p>
          <div style={{ marginTop: 24, fontSize: 13, color: '#8c8c8c', fontStyle: 'italic', textAlign: 'right' }}>
            {t('header.receivedAt', {
              time: selectedNotification?.createdAt ? new Date(selectedNotification.createdAt).toLocaleString(dateLocale()) : '',
            })}
          </div>
        </div>
      </Modal>
    </header>
  );
};

export default Header;
