/**
 * Danh sách app ngân hàng hỗ trợ deeplink VietQR và cách mở app với thông tin
 * chuyển khoản điền sẵn. Tài liệu: https://www.vietqr.io/danh-sach-api/deeplink-app-ngan-hang
 */
import { cachedFetch } from './apiCache';

export type MobilePlatform = 'android' | 'ios';

export interface BankApp {
  appId: string;
  appName: string;
  bankName: string;
  appLogo: string;
  /** App tự điền STK, số tiền, nội dung. Các app khác chỉ được mở lên. */
  autofill: boolean;
  monthlyInstall: number;
}

interface VietQrApp {
  appId: string;
  appName: string;
  bankName: string;
  appLogo: string;
  autofill: number;
  monthlyInstall: number;
}

const BANK_APPS_TTL = 24 * 60 * 60_000;

/** null = máy tính (không có app ngân hàng). */
export function detectMobilePlatform(userAgent: string): MobilePlatform | null {
  if (/iPhone|iPad|iPod/i.test(userAgent)) return 'ios';
  if (/Android/i.test(userAgent)) return 'android';
  return null;
}

/** Thêm app khách chọn và link quay lại trang thanh toán vào deeplink backend trả về. */
export function buildBankAppLink(baseDeeplink: string, appId: string, returnUrl: string): string {
  return `${baseDeeplink}&app=${encodeURIComponent(appId)}&url=${encodeURIComponent(returnUrl)}`;
}

/** App tự điền thông tin lên trước, sau đó app nhiều người dùng hơn. */
export function sortBankApps(apps: BankApp[]): BankApp[] {
  return [...apps].sort((a, b) => Number(b.autofill) - Number(a.autofill) || b.monthlyInstall - a.monthlyInstall);
}

export function fetchBankApps(platform: MobilePlatform): Promise<BankApp[]> {
  return cachedFetch(`vietqr_bank_apps_${platform}`, async () => {
    const res = await fetch(`https://api.vietqr.io/v2/${platform}-app-deeplinks`);
    if (!res.ok) throw new Error(`VietQR ${res.status}`);
    const body = (await res.json()) as { apps: VietQrApp[] };
    const apps = body.apps.map(app => ({
      appId: app.appId,
      appName: app.appName,
      bankName: app.bankName,
      appLogo: app.appLogo,
      autofill: app.autofill === 1,
      monthlyInstall: app.monthlyInstall,
    }));
    return { data: sortBankApps(apps) };
  }, BANK_APPS_TTL);
}
