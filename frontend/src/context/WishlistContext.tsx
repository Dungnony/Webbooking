import React, { useState, useEffect, useCallback } from 'react';
import { WishlistContext } from './wishlistContextValue';
import { wishlistApi } from '../api';
import { AUTH_CHANGED_EVENT } from '../utils/auth';

const GUEST_WISHLIST_KEY = 'wishlist';

const readGuestWishlist = (): number[] => {
  try {
    const stored = localStorage.getItem(GUEST_WISHLIST_KEY);
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
};

const isLoggedInNow = () => !!localStorage.getItem('token');

/**
 * Logged-in users keep their wishlist on the server so it follows them across devices;
 * guests keep it in the browser, and it is merged into their account when they log in.
 */
export const WishlistProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isLoggedIn, setIsLoggedIn] = useState(isLoggedInNow);
  const [wishlist, setWishlist] = useState<number[]>(() => (isLoggedInNow() ? [] : readGuestWishlist()));

  useEffect(() => {
    const onAuthChanged = () => setIsLoggedIn(isLoggedInNow());
    window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
  }, []);

  useEffect(() => {
    if (!isLoggedIn) {
      setWishlist(readGuestWishlist());
      return;
    }
    let cancelled = false;
    const guestIds = readGuestWishlist();
    (guestIds.length > 0 ? wishlistApi.merge(guestIds) : wishlistApi.get())
      .then(ids => {
        if (cancelled) return;
        setWishlist(ids);
        localStorage.removeItem(GUEST_WISHLIST_KEY);
      })
      .catch(err => console.error('Failed to sync wishlist', err));
    return () => { cancelled = true; };
  }, [isLoggedIn]);

  useEffect(() => {
    if (!isLoggedIn) {
      localStorage.setItem(GUEST_WISHLIST_KEY, JSON.stringify(wishlist));
    }
  }, [wishlist, isLoggedIn]);

  // The UI updates immediately; the server's answer then replaces it, or a refetch undoes it on failure.
  const syncWithServer = useCallback((call: () => Promise<number[] | void>) => {
    if (!isLoggedIn) return;
    call()
      .then(ids => { if (Array.isArray(ids)) setWishlist(ids); })
      .catch(err => {
        console.error('Wishlist update failed', err);
        wishlistApi.get().then(setWishlist).catch(() => undefined);
      });
  }, [isLoggedIn]);

  const isInWishlist = (id: number | string) => wishlist.includes(Number(id));

  const addWish = (id: number | string) => {
    const numericId = Number(id);
    if (isNaN(numericId) || isInWishlist(numericId)) return;
    setWishlist(prev => [numericId, ...prev.filter(w => w !== numericId)]);
    syncWithServer(() => wishlistApi.add(numericId));
  };

  const removeWish = (id: number | string) => {
    const numericId = Number(id);
    setWishlist(prev => prev.filter(w => w !== numericId));
    syncWithServer(() => wishlistApi.remove(numericId));
  };

  const toggleWish = (id: number | string) => {
    if (isInWishlist(id)) {
      removeWish(id);
    } else {
      addWish(id);
    }
  };

  const clearAll = () => {
    setWishlist([]);
    syncWithServer(() => wishlistApi.clear());
  };

  return (
    <WishlistContext.Provider
      value={{ wishlist, addWish, removeWish, toggleWish, clearAll, isInWishlist }}
    >
      {children}
    </WishlistContext.Provider>
  );
};
