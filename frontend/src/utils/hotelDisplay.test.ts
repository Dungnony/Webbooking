import { describe, expect, it } from 'vitest';
import { lowestRoomPrice, ratingLabelKey, sortRoomsByPrice } from './hotelDisplay';

describe('lowestRoomPrice', () => {
  it('is the cheapest room, whatever order the rooms come in', () => {
    const hotel = { rooms: [{ pricePerNight: 2_200_000 }, { pricePerNight: 1_200_000 }, { pricePerNight: 5_000_000 }] };

    expect(lowestRoomPrice(hotel)).toBe(1_200_000);
  });

  it('ignores rooms without a valid price', () => {
    const hotel = { rooms: [{ pricePerNight: undefined }, { pricePerNight: 0 }, { pricePerNight: 900_000 }] };

    expect(lowestRoomPrice(hotel)).toBe(900_000);
  });

  it('is null when no room has a price', () => {
    expect(lowestRoomPrice({ rooms: [] })).toBeNull();
    expect(lowestRoomPrice({})).toBeNull();
    expect(lowestRoomPrice({ rooms: [{ pricePerNight: undefined }] })).toBeNull();
  });
});

describe('sortRoomsByPrice', () => {
  it('lists the cheapest room first and rooms without a price last, without changing the input', () => {
    const rooms = [{ id: 1, pricePerNight: 3 }, { id: 2 }, { id: 3, pricePerNight: 1 }];

    expect(sortRoomsByPrice(rooms).map(r => r.id)).toEqual([3, 1, 2]);
    expect(rooms.map(r => r.id)).toEqual([1, 2, 3]);
  });
});

describe('ratingLabelKey', () => {
  it.each([
    [9.5, 'exceptional'],
    [9, 'exceptional'],
    [8.9, 'veryGood'],
    [8, 'veryGood'],
    [7, 'good'],
    [6.7, 'okay'],
    [5, 'okay'],
    [4, 'average'],
    [0.5, 'average'],
  ])('labels %s as %s', (score, key) => {
    expect(ratingLabelKey(score)).toBe(key);
  });

  it('says "not rated yet" when there is no score', () => {
    expect(ratingLabelKey(0)).toBe('none');
    expect(ratingLabelKey(null)).toBe('none');
    expect(ratingLabelKey(undefined)).toBe('none');
  });
});
