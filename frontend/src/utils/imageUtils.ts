export function transformImageUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  if (url.includes('drive.google.com')) {
    const match = url.match(/id=([^&]+)/);
    if (match && match[1]) {
      return `https://drive.google.com/thumbnail?id=${match[1]}&sz=w1000`;
    }
  }
  return url;
}

interface HotelImageSource {
  imageUrl?: string;
  rooms?: { images?: { imageUrl?: string }[] }[];
}

/**
 * Lấy URL ảnh đại diện cho hotel.
 * Ưu tiên: hotel.imageUrl → ảnh đầu tiên của phòng đầu tiên có ảnh → null
 */
export function getHotelImage(hotel: HotelImageSource | null | undefined): string | null {
  const roomImage = hotel?.rooms
    ?.map(room => room.images?.[0]?.imageUrl)
    .find((url): url is string => !!url);
  return transformImageUrl(hotel?.imageUrl || roomImage) ?? null;
}
