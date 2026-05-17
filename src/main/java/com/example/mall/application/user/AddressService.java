package com.example.mall.application.user;

import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<Address> list(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescIdAsc(userId);
    }

    @Transactional
    public Address create(Long userId, AddressCommand cmd) {
        if (cmd.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }
        Address a =
                new Address(
                        userId,
                        cmd.recipient(),
                        cmd.phone(),
                        cmd.province(),
                        cmd.city(),
                        cmd.district(),
                        cmd.detail(),
                        cmd.isDefault());
        return addressRepository.save(a);
    }

    @Transactional
    public Address update(Long userId, Long addressId, AddressCommand cmd) {
        Address a =
                addressRepository
                        .findByIdAndUserId(addressId, userId)
                        .orElseThrow(() -> new NotFoundException("address not found"));
        if (cmd.isDefault() && !a.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }
        a.setRecipient(cmd.recipient());
        a.setPhone(cmd.phone());
        a.setProvince(cmd.province());
        a.setCity(cmd.city());
        a.setDistrict(cmd.district());
        a.setDetail(cmd.detail());
        a.setDefault(cmd.isDefault());
        return a;
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        Address a =
                addressRepository
                        .findByIdAndUserId(addressId, userId)
                        .orElseThrow(() -> new NotFoundException("address not found"));
        addressRepository.delete(a);
    }

    public record AddressCommand(
            String recipient,
            String phone,
            String province,
            String city,
            String district,
            String detail,
            boolean isDefault) {}
}
