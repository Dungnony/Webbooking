import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, DatePicker, Popover } from 'antd';
import { EnvironmentOutlined, CalendarOutlined, TeamOutlined, SearchOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import moment, { Moment } from 'moment';

const { RangePicker } = DatePicker;

interface SearchWidgetProps {
  initialCity?: string;
  initialCheckIn?: string;
  initialCheckOut?: string;
  initialGuests?: number;
}

const SearchWidget: React.FC<SearchWidgetProps> = ({
  initialCity = '',
  initialCheckIn,
  initialCheckOut,
  initialGuests = 2,
}) => {
  const navigate = useNavigate();
  const [city, setCity] = useState(initialCity);
  const [dateRange, setDateRange] = useState<[Moment | null, Moment | null]>(() => {
    return initialCheckIn && initialCheckOut 
      ? [moment(initialCheckIn), moment(initialCheckOut)] 
      : [null, null];
  });
  const [guests, setGuests] = useState(initialGuests);
  const [rooms, setRooms] = useState(1);
  const [guestOpen, setGuestOpen] = useState(false);

  const guestLabel = `${guests} người lớn · ${rooms} phòng`;

  const handleSearch = () => {
    if (!city.trim()) return;
    const params = new URLSearchParams();
    params.set('city', city.trim());
    if (dateRange[0]) params.set('checkIn', dateRange[0].format('YYYY-MM-DD'));
    if (dateRange[1]) params.set('checkOut', dateRange[1].format('YYYY-MM-DD'));
    params.set('guests', String(guests));
    params.set('rooms', String(rooms));
    navigate(`/search?${params.toString()}`);
  };

  const guestContent = (
    <div style={{ width: 260, padding: '4px 0' }}>
      {[
        { label: 'Người lớn', sub: '18 tuổi trở lên', val: guests, set: setGuests, min: 1, max: 30 },
        { label: 'Số phòng', sub: '', val: rooms, set: setRooms, min: 1, max: 30 },
      ].map(({ label, sub, val, set, min, max }) => (
        <div key={label} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
          <div>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{label}</div>
            {sub && <div style={{ fontSize: 12, color: '#8c8c8c' }}>{sub}</div>}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Button
              size="small"
              icon={<MinusOutlined />}
              shape="circle"
              disabled={val <= min}
              onClick={() => set(Math.max(min, val - 1))}
            />
            <span style={{ minWidth: 20, textAlign: 'center', fontWeight: 700 }}>{val}</span>
            <Button
              size="small"
              icon={<PlusOutlined />}
              shape="circle"
              disabled={val >= max}
              onClick={() => set(Math.min(max, val + 1))}
            />
          </div>
        </div>
      ))}
      <Button type="primary" block style={{ marginTop: 10 }} onClick={() => setGuestOpen(false)}>
        Xong
      </Button>
    </div>
  );

  return (
    <div className="search-widget">
      {/* City / Destination */}
      <div className="search-field" style={{ flex: 2 }}>
        <EnvironmentOutlined className="search-field-icon" />
        <div className="search-field-inner">
          <div className="search-field-label">Điểm đến</div>
          <input
            className="search-field-value"
            placeholder="Bạn muốn đến đâu?"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
        </div>
      </div>

      {/* Date range */}
      <div className="search-field" style={{ flex: 2 }}>
        <CalendarOutlined className="search-field-icon" />
        <div className="search-field-inner">
          <div className="search-field-label">Ngày nhận – trả phòng</div>
          <RangePicker
            value={dateRange}
            onChange={(val) => setDateRange(val as [Moment | null, Moment | null])}
            format="YYYY-MM-DD"
            disabledDate={(current) => current && current < moment().startOf('day')}
            placeholder={['Nhận phòng', 'Trả phòng']}
            style={{ border: 'none', boxShadow: 'none', padding: 0, background: 'transparent', width: '100%' }}
            popupStyle={{ zIndex: 1100 }}
          />
        </div>
      </div>

      {/* Guests */}
      <Popover
        content={guestContent}
        trigger="click"
        visible={guestOpen}
        onVisibleChange={setGuestOpen}
        placement="bottomLeft"
      >
        <div className="search-field" style={{ flex: 1.5, cursor: 'pointer' }}>
          <TeamOutlined className="search-field-icon" />
          <div className="search-field-inner">
            <div className="search-field-label">Khách & Phòng</div>
            <div className="search-field-value">{guestLabel}</div>
          </div>
        </div>
      </Popover>

      {/* Search Button */}
      <Button
        className="search-btn"
        icon={<SearchOutlined />}
        onClick={handleSearch}
      >
        Tìm
      </Button>
    </div>
  );
};

export default SearchWidget;
