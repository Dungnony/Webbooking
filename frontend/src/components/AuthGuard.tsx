import React from 'react';
import { Navigate } from 'react-router-dom';

interface AuthGuardProps {
  children: React.ReactNode;
  /** When set, users without this role are sent back to the home page. */
  requiredRole?: 'ADMIN';
}

// Client-side guard is UX only; the backend enforces the same rules on every request.
const AuthGuard: React.FC<AuthGuardProps> = ({ children, requiredRole }) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  if (requiredRole && localStorage.getItem('role') !== requiredRole) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
};

export default AuthGuard;
