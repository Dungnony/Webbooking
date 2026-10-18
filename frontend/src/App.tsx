import { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { WishlistProvider } from './context/WishlistContext';
import AuthGuard from './components/AuthGuard';
import './App.css';

// Each page is its own chunk so the first load only downloads the page being opened.

// ── Auth Pages
const LoginPage = lazy(() => import('./pages/LoginPage'));
const Register = lazy(() => import('./pages/Register'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));

// ── Core Flow
const HomePage = lazy(() => import('./pages/HomePage'));
const SearchResults = lazy(() => import('./pages/SearchResults'));
const HotelDetailPage = lazy(() => import('./pages/HotelDetailPage'));
const HotelReviewAndChatPage = lazy(() => import('./pages/HotelReviewAndChatPage'));
const GuestInfoPage = lazy(() => import('./pages/GuestInfoPage'));
const PaymentPage = lazy(() => import('./pages/PaymentPage'));
const BookingConfirmation = lazy(() => import('./pages/BookingConfirmation'));

// ── Account
const MyBookingsPage = lazy(() => import('./pages/MyBookingsPage'));
const Profile = lazy(() => import('./pages/Profile'));

// ── UX Extras
const Wishlist = lazy(() => import('./pages/Wishlist'));
const Deals = lazy(() => import('./pages/Deals'));

// ── Admin
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'));

const PageFallback = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
    <Spin size="large" />
  </div>
);

function App() {
  return (
    <WishlistProvider>
      <Router>
        <Suspense fallback={<PageFallback />}>
          <Routes>
            {/* ── Root redirect */}
            <Route path="/" element={<HomePage />} />

            {/* ── Auth */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<Register />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />

            {/* ── Core Search & Hotel Flow */}
            <Route path="/search" element={<SearchResults />} />
            <Route path="/hotels/:id" element={<HotelDetailPage />} />
            <Route path="/hotels/:id/reviews" element={<HotelReviewAndChatPage />} />
            <Route path="/booking/:hotelId/guest" element={<GuestInfoPage />} />
            <Route path="/booking/payment" element={<PaymentPage />} />
            <Route path="/booking/confirmation" element={<BookingConfirmation />} />

            {/* ── Account */}
            <Route path="/my-bookings" element={<AuthGuard><MyBookingsPage /></AuthGuard>} />
            <Route path="/profile" element={<AuthGuard><Profile /></AuthGuard>} />

            {/* ── UX Extras */}
            <Route path="/wishlist" element={<Wishlist />} />
            <Route path="/deals" element={<Deals />} />

            {/* ── Admin */}
            <Route path="/admin" element={<AuthGuard requiredRole="ADMIN"><AdminDashboard /></AuthGuard>} />

            {/* ── Fallback */}
            <Route path="/dashboard" element={<Navigate to="/" replace />} />
            <Route path="/welcome" element={<Navigate to="/" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </Router>
    </WishlistProvider>
  );
}

export default App;
