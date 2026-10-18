import { createContext } from 'react';

export interface WishlistContextType {
  wishlist: number[];
  addWish: (id: number | string) => void;
  removeWish: (id: number | string) => void;
  toggleWish: (id: number | string) => void;
  clearAll: () => void;
  isInWishlist: (id: number | string) => boolean;
}

export const WishlistContext = createContext<WishlistContextType | undefined>(undefined);
