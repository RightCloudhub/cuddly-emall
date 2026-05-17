package com.example.mall.web.user;

import com.example.mall.domain.user.Address;

public record AddressResponse(
        Long id,
        String recipient,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        boolean isDefault) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(
                a.getId(),
                a.getRecipient(),
                a.getPhone(),
                a.getProvince(),
                a.getCity(),
                a.getDistrict(),
                a.getDetail(),
                a.isDefault());
    }
}
