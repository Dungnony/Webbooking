// Shared display rules so every page shows the same price and rating label for a hotel.

interface PricedRoom {
  pricePerNight?: number;
}

const hasPrice = (room: PricedRoom): room is PricedRoom & { pricePerNight: number } =>
  typeof room.pricePerNight === 'number' && room.pricePerNight > 0;

/** "From" price of a hotel: its cheapest room. The API returns rooms in no particular order. */
export function lowestRoomPrice(hotel: { rooms?: PricedRoom[] }): number | null {
  const prices = (hotel.rooms ?? []).filter(hasPrice).map(room => room.pricePerNight);
  return prices.length > 0 ? Math.min(...prices) : null;
}

/** Cheapest first, rooms without a price last; returns a new array. */
export function sortRoomsByPrice<T extends PricedRoom>(rooms: T[]): T[] {
  return [...rooms].sort((a, b) => {
    if (!hasPrice(a)) return hasPrice(b) ? 1 : 0;
    if (!hasPrice(b)) return -1;
    return a.pricePerNight - b.pricePerNight;
  });
}

export type RatingLabelKey = 'exceptional' | 'veryGood' | 'good' | 'okay' | 'average' | 'none';

/** Word shown next to a 1–10 review score; no score yet means "not rated". */
export function ratingLabelKey(score: number | null | undefined): RatingLabelKey {
  if (!score || score <= 0) return 'none';
  if (score >= 9) return 'exceptional';
  if (score >= 8) return 'veryGood';
  if (score >= 7) return 'good';
  if (score >= 5) return 'okay';
  return 'average';
}
