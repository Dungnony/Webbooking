/**
 * apiCache.ts – In-memory + sessionStorage cache cho API calls
 * 
 * Cách hoạt động:
 *  1. Lần đầu gọi API: fetch thật, lưu kết quả vào memory + sessionStorage
 *  2. Lần sau (trong cùng phiên): trả về ngay từ cache, KHÔNG gọi network
 *  3. Cache tự hết hạn sau TTL (mặc định 60 giây)
 */

interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number; // ms
}

// In-memory cache (nhanh nhất, mất khi refresh trang)
const memoryCache = new Map<string, CacheEntry<unknown>>();

// TTL mặc định: 60 giây
const DEFAULT_TTL = 60_000;

// TTL cho danh sách khách sạn (ít thay đổi): 5 phút
export const HOTEL_LIST_TTL = 5 * 60_000;

// TTL cho chi tiết khách sạn: 2 phút
export const HOTEL_DETAIL_TTL = 2 * 60_000;

// TTL cho thông báo (thay đổi nhiều): 30 giây
export const NOTIFICATION_TTL = 30_000;

/** Shared so the review form can invalidate the totals cached by the hotel detail page. */
export const reviewCacheKey = (hotelId: number) => `reviews_hotel_${hotelId}`;

function isExpired<T>(entry: CacheEntry<T>): boolean {
  return Date.now() - entry.timestamp > entry.ttl;
}

/**
 * Lấy data từ cache nếu còn hiệu lực
 */
export function getCache<T>(key: string): T | null {
  // Ưu tiên memory cache
  const memEntry = memoryCache.get(key);
  if (memEntry && !isExpired(memEntry)) {
    return memEntry.data as T;
  }

  // Fallback sessionStorage (giữ qua navigate)
  try {
    const raw = sessionStorage.getItem(`api_cache_${key}`);
    if (raw) {
      const entry: CacheEntry<T> = JSON.parse(raw);
      if (!isExpired(entry)) {
        // Repopulate memory cache
        memoryCache.set(key, entry);
        return entry.data;
      }
      sessionStorage.removeItem(`api_cache_${key}`);
    }
  } catch {
    // sessionStorage có thể bị block trong một số trình duyệt
  }

  return null;
}

/**
 * Lưu data vào cache
 */
export function setCache<T>(key: string, data: T, ttl = DEFAULT_TTL): void {
  const entry: CacheEntry<T> = { data, timestamp: Date.now(), ttl };
  memoryCache.set(key, entry);

  try {
    sessionStorage.setItem(`api_cache_${key}`, JSON.stringify(entry));
  } catch {
    // Ignore nếu sessionStorage đầy
  }
}

/**
 * Xóa một key khỏi cache (dùng sau khi mutate data)
 */
export function invalidateCache(key: string): void {
  memoryCache.delete(key);
  try {
    sessionStorage.removeItem(`api_cache_${key}`);
  } catch {
    //
  }
}

/**
 * Xóa tất cả cache bắt đầu bằng prefix
 */
export function invalidateCachePrefix(prefix: string): void {
  for (const key of memoryCache.keys()) {
    if (key.startsWith(prefix)) memoryCache.delete(key);
  }
  try {
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const k = sessionStorage.key(i);
      if (k && k.startsWith(`api_cache_${prefix}`)) {
        sessionStorage.removeItem(k);
      }
    }
  } catch {
    //
  }
}

/**
 * Wrapper tiện lợi: tự động cache kết quả của một async function
 * 
 * Ví dụ:
 *   const hotels = await cachedFetch('hotels_all', () => hotelApi.getAll(), HOTEL_LIST_TTL);
 */
export async function cachedFetch<T>(
  key: string,
  fetcher: () => Promise<{ data: T }>,
  ttl = DEFAULT_TTL
): Promise<T> {
  const cached = getCache<T>(key);
  if (cached !== null) {
    return cached;
  }

  const res = await fetcher();
  const data = res.data;
  setCache(key, data, ttl);
  return data;
}
