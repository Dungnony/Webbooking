import React, { useState, useEffect } from 'react';
import { WishlistContext } from './wishlistContextValue';

export const WishlistProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [wishlist, setWishlist] = useState<number[]>(() => {
    try {
      const stored = localStorage.getItem('wishlist');
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    localStorage.setItem('wishlist', JSON.stringify(wishlist));
  }, [wishlist]);

  const addWish = (id: number | string) => {
    const numericId = Number(id);
    if (!isNaN(numericId)) {
      setWishlist((prev) => Array.from(new Set([...prev, numericId])));
    }
  };

  const removeWish = (id: number | string) => {
    const numericId = Number(id);
    setWishlist((prev) => prev.filter((w) => w !== numericId));
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
  };

  const isInWishlist = (id: number | string) => {
    return wishlist.includes(Number(id));
  };

  return (
    <WishlistContext.Provider
      value={{ wishlist, addWish, removeWish, toggleWish, clearAll, isInWishlist }}
    >
      {children}
    </WishlistContext.Provider>
  );
};
