import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Row, Col, Input, Button, message, Divider } from 'antd';
import { SendOutlined, StarFilled, LeftOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { LOGIN_REQUIRED_REPLY } from '../components/AIAssistantWidget';
import { hotelApi, reviewApi, chatApi, ChatMessage, HotelWithStats, ReviewEligibility, ReviewItem } from '../api';
import { getErrorMessage } from '../utils/errors';
import { invalidateCache, invalidateCachePrefix, reviewCacheKey } from '../utils/apiCache';


/* ── Helper Functions ── */
const getRatingLabel = (score: number) => {
  if (score >= 9) return 'Xuất sắc';
  if (score >= 8) return 'Rất tốt';
  if (score >= 7) return 'Tốt';
  if (score >= 5) return 'Ổn';
  return 'Trung bình';
};

const getRatingColor = (score: number) => {
  if (score >= 9) return '#008234';
  if (score >= 7) return '#006ce4';
  if (score >= 5) return '#f56600';
  return '#d4111e';
};

const formatTimeAgo = (dateStr: string) => {
  const d = new Date(dateStr);
  const now = new Date();
  const diff = Math.floor((now.getTime() - d.getTime()) / 1000);
  if (diff < 60) return 'Vừa xong';
  if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`;
  if (diff < 2592000) return `${Math.floor(diff / 86400)} ngày trước`;
  return d.toLocaleDateString('vi-VN');
};

interface StarRatingProps {
  value: number;
  hover?: number;
  onChange?: (v: number) => void;
  onHover?: (v: number) => void;
  onLeave?: () => void;
  size?: number;
  count?: number;
  readonly?: boolean;
}

const StarRating = ({
  value,
  hover,
  onChange,
  onHover,
  onLeave,
  size = 28,
  count = 10,
  readonly = false,
}: StarRatingProps) => {
  const active = hover || value;
  return (
    <div style={{ display: 'flex', gap: 2, alignItems: 'center' }}>
      {Array.from({ length: count }, (_, i) => i + 1).map((star) => (
        <span
          key={star}
          style={{
            cursor: readonly ? 'default' : 'pointer',
            fontSize: size,
            color: star <= active ? '#febb02' : '#d9d9d9',
            transition: 'color 0.15s, transform 0.15s',
            transform: !readonly && star <= (hover || 0) ? 'scale(1.15)' : 'scale(1)',
            lineHeight: 1,
          }}
          onClick={() => !readonly && onChange?.(star)}
          onMouseEnter={() => !readonly && onHover?.(star)}
          onMouseLeave={() => !readonly && onLeave?.()}
        >
          <StarFilled />
        </span>
      ))}
    </div>
  );
};

const HotelReviewAndChatPage = ({ isDrawer }: { isDrawer?: boolean }) => {
  const { id: paramsId } = useParams<{ id: string }>();
  const id = paramsId || window.location.pathname.split('/')[2];
  const navigate = useNavigate();
  const [hotel, setHotel] = useState<HotelWithStats | null>(null);

  // State đánh giá
  const [reviews, setReviews] = useState<ReviewItem[]>([]);
  const [reviewStats, setReviewStats] = useState<{ avg: number | null; total: number }>({ avg: null, total: 0 });
  const [nextReviewPage, setNextReviewPage] = useState(0);
  const [hasMoreReviews, setHasMoreReviews] = useState(false);
  const [loadingMoreReviews, setLoadingMoreReviews] = useState(false);
  const [eligibility, setEligibility] = useState<ReviewEligibility | null>(null);
  const [reviewRating, setReviewRating] = useState(0);
  const [reviewHover, setReviewHover] = useState(0);
  const [reviewComment, setReviewComment] = useState('');
  const [submittingReview, setSubmittingReview] = useState(false);

  // State trò chuyện
  const [messages, setMessages] = useState<{ sender: 'user' | 'bot', text: string, time: Date }[]>([
    { sender: 'bot', text: 'Xin chào! Tôi là trợ lý ảo của khách sạn. Tôi có thể giúp gì cho bạn?', time: new Date() }
  ]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);

  const isLoggedIn = !!localStorage.getItem('token');

  // The backend sorts newest first and pages the list; the average covers every review.
  const loadReviews = useCallback(async (page: number) => {
    const { data } = await reviewApi.getForHotel(Number(id), page);
    setReviews(prev => (page === 0 ? data.content : [...prev, ...data.content]));
    setReviewStats({ avg: data.averageRating, total: data.totalElements });
    setNextReviewPage(page + 1);
    setHasMoreReviews(page + 1 < data.totalPages);
  }, [id]);

  const fetchReviews = useCallback(() => {
    loadReviews(0).catch(err => console.error('Failed to load reviews', err));
    reviewApi.getEligibility(Number(id))
      .then(res => setEligibility(res.data))
      .catch(err => console.error('Failed to load review eligibility', err));
  }, [id, loadReviews]);

  const handleLoadMoreReviews = async () => {
    setLoadingMoreReviews(true);
    try {
      await loadReviews(nextReviewPage);
    } catch (err) {
      message.error(getErrorMessage(err, 'Không thể tải thêm đánh giá.'));
    } finally {
      setLoadingMoreReviews(false);
    }
  };

  useEffect(() => {
    if (!id) return;
    hotelApi.getById(Number(id))
      .then(res => setHotel(res.data))
      .catch(err => console.error('Failed to load hotel', err));
    fetchReviews();
  }, [id, fetchReviews]);

  const handleSubmitReview = async () => {
    if (!reviewRating) return message.warning('Vui lòng chọn điểm đánh giá!');
    if (!reviewComment.trim()) return message.warning('Vui lòng nhập nhận xét!');

    setSubmittingReview(true);
    try {
      // The backend attaches the review to the logged-in user, so no author is sent.
      await reviewApi.create({
        rating: reviewRating,
        comment: reviewComment,
        hotel: { id: Number(id) },
      });
      message.success('🎉 Đánh giá của bạn đã được gửi thành công!');
      setReviewRating(0);
      setReviewComment('');
      // The hotel's rating changed: drop cached totals and hotel data so other pages refetch them.
      invalidateCache(reviewCacheKey(Number(id)));
      invalidateCachePrefix('hotel');
      fetchReviews();
    } catch (e) {
      message.error(getErrorMessage(e, 'Gửi đánh giá thất bại!'));
    } finally {
      setSubmittingReview(false);
    }
  };

const handleSendMessage = async () => {
  if (!chatInput.trim() || chatLoading) return;

  const userText = chatInput.trim();
  setChatInput('');
  setMessages(prev => [...prev, { sender: 'user', text: userText, time: new Date() }]);
  if (!isLoggedIn) {
    setMessages(prev => [...prev, { sender: 'bot', text: LOGIN_REQUIRED_REPLY, time: new Date() }]);
    return;
  }
  const newHistory: ChatMessage[] = [...chatHistory, { role: 'user', content: userText }];
  setChatHistory(newHistory);
  setChatLoading(true);

  try {
    const data = await chatApi.sendMessage(newHistory, {
      name: hotel?.name,
      description: hotel?.description,
      address: hotel?.address,
      city: hotel?.city,
      ratingAvg: hotel?.ratingAvg
    });

    const botReply = data.reply || 'Xin lỗi, tôi không hiểu câu hỏi này.';

    setMessages(prev => [...prev, { sender: 'bot', text: botReply, time: new Date() }]);
    setChatHistory(prev => [...prev, { role: 'assistant', content: botReply }]);
  } catch {
    setMessages(prev => [...prev, { sender: 'bot', text: '⚠️ Lỗi kết nối. Vui lòng thử lại sau.', time: new Date() }]);
  } finally {
    setChatLoading(false);
  }
};
  const hotelName = hotel?.name || 'Khách sạn';

  const content = (
    <div className="container" style={{ padding: isDrawer ? '0' : '24px' }}>
      {!isDrawer && (
        <Button 
          type="link" 
          icon={<LeftOutlined />} 
          onClick={() => navigate(`/hotels/${id}`)}
          style={{ padding: 0, marginBottom: 16, fontSize: 14, fontWeight: 600 }}
        >
          Quay lại {hotelName}
        </Button>
      )}

        <Row gutter={[24, 24]}>
          {/* ── Trái: Đánh giá ── */}
          <Col xs={24} lg={15}>
            <div style={{ background: '#fff', borderRadius: 8, padding: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <h2 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>Đánh giá của khách</h2>
              </div>

              {/* Review Summary */}
              {reviewStats.avg !== null && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24, padding: 16, background: '#f8f9fa', borderRadius: 8 }}>
                  <div style={{ background: getRatingColor(reviewStats.avg), color: '#fff', borderRadius: '6px 6px 6px 0', padding: '10px 16px', fontSize: 24, fontWeight: 800, lineHeight: 1 }}>
                    {reviewStats.avg.toFixed(1)}
                  </div>
                  <div>
                    <div style={{ fontWeight: 800, fontSize: 18, color: '#1a1a1a' }}>{getRatingLabel(reviewStats.avg)}</div>
                    <div style={{ color: '#595959', fontSize: 14 }}>Dựa trên {reviewStats.total} đánh giá</div>
                  </div>
                </div>
              )}

              {/* Write Review */}
              {eligibility && !eligibility.canReview && eligibility.reason && (
                <div style={{ marginBottom: 24, padding: 16, background: '#f8f9fa', borderRadius: 8, color: '#595959', fontSize: 14 }}>
                  {eligibility.reason}
                </div>
              )}
              {eligibility?.canReview && (
                <div style={{ marginBottom: 24, border: '1px solid #e2e8f0', borderRadius: 8, padding: 20 }}>
                  <h3 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 700 }}>✍️ Chia sẻ trải nghiệm của bạn</h3>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ marginBottom: 8, fontWeight: 600 }}>Điểm đánh giá (1-10):</div>
                    <StarRating
                      value={reviewRating}
                      hover={reviewHover}
                      onChange={setReviewRating}
                      onHover={setReviewHover}
                      onLeave={() => setReviewHover(0)}
                    />
                  </div>
                  <Input.TextArea
                    rows={3}
                    placeholder="Nhận xét của bạn về chỗ nghỉ..."
                    value={reviewComment}
                    onChange={(e) => setReviewComment(e.target.value)}
                    style={{ marginBottom: 16, borderRadius: 6 }}
                  />
                  <Button type="primary" onClick={handleSubmitReview} loading={submittingReview} style={{ fontWeight: 600, height: 38 }}>
                    Gửi đánh giá
                  </Button>
                </div>
              )}

              <Divider />

              {/* Reviews List */}
              <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 16 }}>Tất cả đánh giá ({reviewStats.total})</h3>
              {reviews.length > 0 ? (
                reviews.map((review, idx) => (
                  <div key={review.id ?? idx} style={{ padding: '20px 0', borderBottom: idx < reviews.length - 1 ? '1px solid #f0f0f0' : 'none', display: 'flex', gap: 16 }}>
                    <div style={{
                      width: 48, height: 48, borderRadius: '50%', background: `hsl(${((review.username || 'U').charCodeAt(0) * 47) % 360}, 55%, 45%)`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 20, flexShrink: 0
                    }}>
                      {(review.username || 'U')[0].toUpperCase()}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: 15 }}>{review.username || 'Khách'}</div>
                          <div style={{ fontSize: 12, color: '#8c8c8c' }}>{review.createdAt ? formatTimeAgo(review.createdAt) : ''}</div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <div style={{ background: getRatingColor(review.rating ?? 0), color: '#fff', borderRadius: '4px 4px 4px 0', padding: '4px 10px', fontWeight: 800, fontSize: 15 }}>
                            {review.rating}
                          </div>
                          <span style={{ fontSize: 13, fontWeight: 600, color: getRatingColor(review.rating ?? 0) }}>
                            {getRatingLabel(review.rating ?? 0)}
                          </span>
                        </div>
                      </div>
                      <div style={{ fontSize: 14, color: '#333', lineHeight: 1.6 }}>{review.comment}</div>
                      {review.hotelReply && (
                        <div style={{ marginTop: 12, background: '#f0f5ff', borderLeft: '3px solid #006ce4', borderRadius: 4, padding: '10px 14px' }}>
                          <div style={{ fontWeight: 700, fontSize: 13, color: '#003b95', marginBottom: 4 }}>
                            Phản hồi từ {hotel?.name || 'khách sạn'}
                            {review.hotelRepliedAt && (
                              <span style={{ fontWeight: 400, color: '#8c8c8c', marginLeft: 8 }}>{formatTimeAgo(review.hotelRepliedAt)}</span>
                            )}
                          </div>
                          <div style={{ fontSize: 14, color: '#333', lineHeight: 1.6 }}>{review.hotelReply}</div>
                        </div>
                      )}
                    </div>
                  </div>
                ))
              ) : (
                <div style={{ textAlign: 'center', padding: '40px 0', color: '#8c8c8c' }}>Chưa có đánh giá nào.</div>
              )}
              {hasMoreReviews && (
                <div style={{ textAlign: 'center', marginTop: 16 }}>
                  <Button onClick={handleLoadMoreReviews} loading={loadingMoreReviews}>Xem thêm đánh giá</Button>
                </div>
              )}
            </div>
          </Col>

          {/* ── Phải: Khung Chat ── */}
          <Col xs={24} lg={9}>
            <div style={{ background: '#fff', borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.06)', display: 'flex', flexDirection: 'column', height: 'calc(100vh - 120px)', position: 'sticky', top: 24 }}>
              
              {/* Chat Header */}
              <div style={{ padding: '16px 20px', borderBottom: '1px solid #f0f0f0', background: '#003b95', borderRadius: '8px 8px 0 0', color: '#fff' }}>
                <h3 style={{ margin: 0, color: '#fff', fontSize: 16, fontWeight: 700 }}>Chat với Khách sạn</h3>
                <div style={{ fontSize: 12, opacity: 0.8 }}>Hỗ trợ trực tuyến / Bot AI</div>
              </div>

              {/* Chat Messages */}
              <div style={{ flex: 1, padding: 20, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 16, background: '#f9fafd' }}>
                                  {/* Quick suggestions - chỉ hiện khi mới vào */}
                {messages.length === 1 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {[
                      '🏨 Khách sạn có những tiện nghi gì?',
                      '💰 Giá phòng bao nhiêu?',
                      '📅 Check-in lúc mấy giờ?',
                      '🚗 Đi đến khách sạn thế nào?',
                      '🔄 Chính sách huỷ phòng?',
                    ].map(q => (
                      <button
                        key={q}
                        onClick={() => { setChatInput(q); }}
                        style={{
                          background: '#f0f6ff', border: '1px solid #c8deff',
                          borderRadius: 16, padding: '4px 12px',
                          fontSize: 12, color: '#006ce4', cursor: 'pointer',
                          transition: 'all .15s',
                        }}
                        onMouseEnter={e => { (e.target as HTMLElement).style.background = '#006ce4'; (e.target as HTMLElement).style.color = '#fff'; }}
                        onMouseLeave={e => { (e.target as HTMLElement).style.background = '#f0f6ff'; (e.target as HTMLElement).style.color = '#006ce4'; }}
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                )}
                {messages.map((msg, idx) => (
                  <div key={idx} style={{ display: 'flex', flexDirection: 'column', alignItems: msg.sender === 'user' ? 'flex-end' : 'flex-start' }}>
                    <div style={{
                      maxWidth: '85%',
                      padding: '10px 14px',
                      borderRadius: msg.sender === 'user' ? '12px 12px 0 12px' : '12px 12px 12px 0',
                      background: msg.sender === 'user' ? '#006ce4' : '#fff',
                      color: msg.sender === 'user' ? '#fff' : '#1a1a1a',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                      fontSize: 14,
                      lineHeight: 1.5,
                      border: msg.sender === 'bot' ? '1px solid #e7e7e7' : 'none',
                    }}>
                      {msg.text}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
                      {msg.time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>
                ))}
              </div>

              {chatLoading && (
              <div style={{ display: 'flex', alignItems: 'flex-start' }}>
                <div style={{
                  padding: '10px 16px',
                  borderRadius: '12px 12px 12px 0',
                  background: '#fff',
                  border: '1px solid #e7e7e7',
                  display: 'flex', gap: 4, alignItems: 'center'
                }}>
                  {[0,1,2].map(i => (
                    <span key={i} style={{
                      width: 7, height: 7, borderRadius: '50%', background: '#aaa', display: 'inline-block',
                      animation: 'dot 1.2s infinite', animationDelay: `${i * 0.2}s`
                    }} />
                  ))}
                </div>
              </div>
            )}

              {/* Chat Input */}
              <div style={{ padding: 16, borderTop: '1px solid #f0f0f0', background: '#fff', borderRadius: '0 0 8px 8px' }}>
                <div style={{ display: 'flex', gap: 8 }}>
            <Input
              placeholder={chatLoading ? 'Đang trả lời...' : 'Nhập tin nhắn...'}
              value={chatInput}
              onChange={e => setChatInput(e.target.value)}
              onPressEnter={handleSendMessage}
              disabled={chatLoading}
              style={{ borderRadius: 20 }}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              shape="circle"
              onClick={handleSendMessage}
              disabled={chatLoading}
              style={{ flexShrink: 0 }}
            />
                </div>
              </div>
            </div>
          </Col>
        </Row>
      </div>
  );

  if (isDrawer) {
    return content;
  }

  return (
    <div className="page-wrapper page-slide-in-right" style={{ background: '#f5f5f5', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />
      {content}
      <Footer />
    </div>
  );
};

export default HotelReviewAndChatPage;
