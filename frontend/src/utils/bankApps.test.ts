import { describe, expect, it } from 'vitest';
import { buildBankAppLink, detectMobilePlatform, sortBankApps, type BankApp } from './bankApps';

const BASE = 'https://dl.vietqr.io/pay?ba=10001473192@tpb&am=1500000&tn=DH+42&bn=NGUYEN+DUY+TU';

const app = (appId: string, autofill: boolean, monthlyInstall: number): BankApp => ({
  appId, appName: appId, bankName: appId, appLogo: '', autofill, monthlyInstall,
});

describe('buildBankAppLink', () => {
  it('keeps the prefilled transfer and adds the chosen app and the return page', () => {
    const link = buildBankAppLink(BASE, 'mb', 'https://web.vn/booking/payment?bookingId=42');

    expect(link).toBe(`${BASE}&app=mb&url=https%3A%2F%2Fweb.vn%2Fbooking%2Fpayment%3FbookingId%3D42`);
    const params = new URL(link).searchParams;
    expect(params.get('tn')).toBe('DH 42');
    expect(params.get('url')).toBe('https://web.vn/booking/payment?bookingId=42');
  });
});

describe('detectMobilePlatform', () => {
  it('recognises iOS, Android and desktop browsers', () => {
    expect(detectMobilePlatform('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')).toBe('ios');
    expect(detectMobilePlatform('Mozilla/5.0 (Linux; Android 14; Pixel 8)')).toBe('android');
    expect(detectMobilePlatform('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBeNull();
  });
});

describe('sortBankApps', () => {
  it('lists apps that prefill the transfer first, then the most installed', () => {
    const sorted = sortBankApps([app('tpb', false, 90_000), app('acb', true, 60_000), app('mb', true, 400_000)]);

    expect(sorted.map(a => a.appId)).toEqual(['mb', 'acb', 'tpb']);
  });
});
