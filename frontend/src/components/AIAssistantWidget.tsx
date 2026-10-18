import React, { useState, useRef, useEffect } from 'react';
import { chatApi, ChatMessage } from '../api';

type Message = ChatMessage;

export const LOGIN_REQUIRED_REPLY = '🔒 Vui lòng đăng nhập để sử dụng trợ lý AI.';

const QUICK_QUESTIONS = [
  { label: '🏨 Xem danh sách khách sạn', value: 'Website có những khách sạn nào ở Hà Nội? Liệt kê tên và địa chỉ.' },
  { label: '💰 Giá phòng bao nhiêu?',    value: 'Giá phòng các loại tại các khách sạn là bao nhiêu? Loại nào rẻ nhất?' },
  { label: '📅 Cách đặt phòng',          value: 'Tôi cần làm gì để đặt phòng khách sạn trên website này?' },
  { label: '🔄 Đổi ngày / Huỷ phòng',   value: 'Tôi muốn đổi ngày hoặc huỷ đặt phòng thì làm thế nào?' },
  { label: '💳 Thanh toán thế nào?',     value: 'Các hình thức thanh toán được hỗ trợ là gì? Có hoàn tiền không?' },
  { label: '⭐ Phòng nào được đánh giá cao nhất?', value: 'Khách sạn nào được đánh giá cao nhất? Cho tôi xem rating.' },
  { label: '👨‍👩‍👧 Phòng gia đình',          value: 'Có những loại phòng nào phù hợp cho gia đình đông người?' },
  { label: '🛎️ Dịch vụ có gì?',          value: 'Các khách sạn có những dịch vụ đặc biệt gì như spa, rooftop, bar?' },
];



const AIAssistantWidget: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [showQuick, setShowQuick] = useState(true);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const sendMessage = async (text: string) => {
    if (!text.trim() || loading) return;
    setShowQuick(false);
    const userMsg: Message = { role: 'user', content: text };
    const updated = [...messages, userMsg];
    setInput('');
    // The chat endpoint requires authentication so anonymous visitors cannot spend the AI quota.
    if (!localStorage.getItem('token')) {
      setMessages([...updated, { role: 'assistant', content: LOGIN_REQUIRED_REPLY }]);
      return;
    }
    setMessages(updated);
    setLoading(true);
    try {
      // 🟢 Thay vì gọi callOpenAI, giờ ta gọi chatApi chọc xuống Backend
      const data = await chatApi.sendMessage(updated, {
        name: "Hệ thống Booking Nhom2",
        description: "Hệ thống đặt phòng trực tuyến tại Hà Nội.",
        city: "Hà Nội",
        ratingAvg: 9.5
      });
      
      const botReply = data.reply || 'Xin lỗi, tôi không hiểu câu hỏi này.';
      setMessages([...updated, { role: 'assistant', content: botReply }]);
    } catch {
      setMessages([...updated, { role: 'assistant', content: '⚠️ Lỗi kết nối. Vui lòng thử lại sau.' }]);
    } finally {
      setLoading(false);
    }
  };

  const reset = () => { setMessages([]); setShowQuick(true); setInput(''); };

  return (
    <>
      <style>{`
        .ai-fab {
          position: fixed; bottom: 28px; right: 28px;
          width: 56px; height: 56px; border-radius: 50%;
          background: linear-gradient(135deg, #006ce4, #0099ff);
          color: #fff; border: none; cursor: pointer; font-size: 26px;
          display: flex; align-items: center; justify-content: center;
          box-shadow: 0 4px 20px rgba(0,108,228,0.45); z-index: 9998;
          transition: transform .2s, box-shadow .2s;
        }
        .ai-fab:hover { transform: scale(1.1); box-shadow: 0 6px 28px rgba(0,108,228,0.6); }
        .ai-badge {
          position: absolute; top: -4px; right: -4px;
          width: 18px; height: 18px; background: #ff4d4f;
          border-radius: 50%; border: 2px solid #fff;
          animation: ai-pulse 1.5s infinite;
        }
        @keyframes ai-pulse {
          0%,100% { transform: scale(1); } 50% { transform: scale(1.25); }
        }
        .ai-window {
          position: fixed; bottom: 96px; right: 28px; width: 360px;
          max-height: 560px; background: #fff; border-radius: 16px;
          box-shadow: 0 8px 40px rgba(0,0,0,0.18);
          display: flex; flex-direction: column; overflow: hidden;
          z-index: 9999; animation: ai-slide-up .25s ease;
        }
        @keyframes ai-slide-up {
          from { opacity:0; transform: translateY(20px); }
          to { opacity:1; transform: translateY(0); }
        }
        .ai-header {
          background: linear-gradient(135deg, #006ce4, #0099ff);
          color: #fff; padding: 14px 16px;
          display: flex; align-items: center; gap: 10px;
        }
        .ai-avatar {
          width: 38px; height: 38px; border-radius: 50%;
          background: rgba(255,255,255,0.25);
          display: flex; align-items: center; justify-content: center; font-size: 20px;
        }
        .ai-hname { font-weight: 700; font-size: 15px; }
        .ai-hstatus { font-size: 12px; opacity: .85; }
        .ai-hactions { display: flex; gap: 6px; margin-left: auto; }
        .ai-ibtn {
          background: rgba(255,255,255,0.2); border: none; color: #fff;
          cursor: pointer; width: 28px; height: 28px; border-radius: 50%;
          display: flex; align-items: center; justify-content: center;
          font-size: 14px; transition: background .2s;
        }
        .ai-ibtn:hover { background: rgba(255,255,255,0.35); }
        .ai-body {
          flex: 1; overflow-y: auto; padding: 14px 14px 6px;
          display: flex; flex-direction: column; gap: 10px;
          min-height: 200px; max-height: 340px;
        }
        .ai-body::-webkit-scrollbar { width: 4px; }
        .ai-body::-webkit-scrollbar-thumb { background: #d0d0d0; border-radius: 4px; }
        .ai-welcome {
          background: #f0f6ff; border-radius: 12px;
          padding: 12px 14px; font-size: 13.5px; color: #333; line-height: 1.5;
        }
        .ai-welcome b { color: #006ce4; }
        .ai-qlabel { font-size: 12px; color: #888; margin-bottom: 4px; }
        .ai-qgrid { display: flex; flex-wrap: wrap; gap: 7px; }
        .ai-qbtn {
          background: #f0f6ff; border: 1px solid #c8deff;
          border-radius: 20px; padding: 5px 12px;
          font-size: 12.5px; color: #006ce4; cursor: pointer;
          transition: all .15s; white-space: nowrap;
        }
        .ai-qbtn:hover { background: #006ce4; color: #fff; border-color: #006ce4; }
        .ai-row { display: flex; gap: 8px; align-items: flex-end; }
        .ai-row.user { flex-direction: row-reverse; }
        .ai-avsm {
          width: 28px; height: 28px; border-radius: 50%;
          background: linear-gradient(135deg, #006ce4, #0099ff);
          color: #fff; display: flex; align-items: center;
          justify-content: center; font-size: 14px; flex-shrink: 0;
        }
        .ai-bubble {
          max-width: 80%; padding: 9px 13px; border-radius: 14px;
          font-size: 13.5px; line-height: 1.5; word-break: break-word;
        }
        .ai-bubble.assistant { background: #f4f4f4; color: #222; border-bottom-left-radius: 4px; }
        .ai-bubble.user {
          background: linear-gradient(135deg, #006ce4, #0099ff);
          color: #fff; border-bottom-right-radius: 4px;
        }
        .ai-dots { display: flex; gap: 4px; align-items: center; padding: 4px 0; }
        .ai-dots span {
          width: 7px; height: 7px; background: #aaa; border-radius: 50%;
          animation: ai-dot 1.2s infinite;
        }
        .ai-dots span:nth-child(2) { animation-delay: .2s; }
        .ai-dots span:nth-child(3) { animation-delay: .4s; }
        @keyframes ai-dot {
          0%,80%,100% { transform: scale(1); opacity:.5; }
          40% { transform: scale(1.4); opacity:1; }
        }
        .ai-footer {
          padding: 10px 12px; border-top: 1px solid #f0f0f0;
          display: flex; gap: 8px; align-items: center;
        }
        .ai-input {
          flex: 1; border: 1.5px solid #e0e0e0; border-radius: 22px;
          padding: 8px 14px; font-size: 13.5px; outline: none;
          transition: border .2s; background: #fafafa;
        }
        .ai-input:focus { border-color: #006ce4; background: #fff; }
        .ai-sendbtn {
          width: 36px; height: 36px; border-radius: 50%;
          background: linear-gradient(135deg, #006ce4, #0099ff);
          border: none; cursor: pointer; color: #fff; font-size: 16px;
          display: flex; align-items: center; justify-content: center;
          transition: opacity .2s; flex-shrink: 0;
        }
        .ai-sendbtn:disabled { opacity: .5; cursor: not-allowed; }
        .ai-sendbtn:not(:disabled):hover { opacity: .85; }
        @media (max-width: 480px) {
          .ai-window { right:0; left:0; width:100%; bottom:76px; border-radius:16px 16px 0 0; }
          .ai-fab { bottom:18px; right:18px; }
        }
      `}</style>

      {/* FAB */}
    <button className="ai-fab" onClick={() => setOpen(v => !v)} title="Trợ lý AI" aria-label="Mở trợ lý AI">
    {open
        ? <span style={{ fontSize: 20 }}>✕</span>
        : <img src="/logo-ai.png" style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover' }} alt="AI" />
    }
    {!open && <span className="ai-badge" />}
    </button>

      {/* Chat window */}
      {open && (
        <div className="ai-window">
          <div className="ai-header">
            <div className="ai-avatar">
            <img src="/logo-ai.png" style={{ width: 60, height: 60, borderRadius: '50%', objectFit: 'cover' }} alt="AI" />
            </div>
            <div>
              <div className="ai-hname">Trợ lý AI Booking</div>
              <div className="ai-hstatus">🟢 Trực tuyến • Phản hồi ngay</div>
            </div>
            <div className="ai-hactions">
              <button className="ai-ibtn" onClick={reset} title="Cuộc trò chuyện mới">↺</button>
              <button className="ai-ibtn" onClick={() => setOpen(false)} title="Đóng">✕</button>
            </div>
          </div>

          <div className="ai-body">
            {messages.length === 0 && (
              <div className="ai-welcome">
                👋 Xin chào! Mình là <b>Trợ lý AI</b> của website đặt phòng.<br />
                Mình có thể giúp bạn tìm khách sạn, tư vấn đặt phòng, thanh toán và nhiều hơn nữa!
              </div>
            )}

            {showQuick && (
              <div>
                <div className="ai-qlabel">Câu hỏi thường gặp:</div>
                <div className="ai-qgrid">
                  {QUICK_QUESTIONS.map(q => (
                    <button key={q.value} className="ai-qbtn" onClick={() => sendMessage(q.value)}>
                      {q.label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((msg, i) => (
              <div key={i} className={`ai-row ${msg.role}`}>
                {msg.role === 'assistant' &&                 
                <div className="ai-avsm">
                <img src="/logo-ai.png" style={{ width: 40, height: 40, borderRadius: '50%', objectFit: 'cover' }} alt="AI" />
                </div>}
                <div className={`ai-bubble ${msg.role}`}>{msg.content}</div>
              </div>
            ))}

            {loading && (
              <div className="ai-row">
                <div className="ai-avsm">
                <img src="/logo-ai.png" style={{ width: 40, height: 40, borderRadius: '50%', objectFit: 'cover' }} alt="AI" />
                </div>
                <div className="ai-bubble assistant">
                  <div className="ai-dots"><span /><span /><span /></div>
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          <div className="ai-footer">
            <input
              className="ai-input"
              placeholder="Nhập câu hỏi về đặt phòng..."
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && sendMessage(input)}
              disabled={loading}
            />
            <button
              className="ai-sendbtn"
              onClick={() => sendMessage(input)}
              disabled={loading || !input.trim()}
              aria-label="Gửi"
            >
              ➤
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default AIAssistantWidget;