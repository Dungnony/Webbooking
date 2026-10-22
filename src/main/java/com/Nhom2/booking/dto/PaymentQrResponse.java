package com.Nhom2.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrResponse {

    private Long bookingId;
    private String qrCodeUrl;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private Double amount;
    private String transferContent;
    /**
     * Deeplink VietQR mo app ngan hang voi STK, so tien, noi dung dien san.
     * Frontend noi them "&app=<ma app>&url=<link quay lai>".
     */
    private String bankAppDeeplink;
}
