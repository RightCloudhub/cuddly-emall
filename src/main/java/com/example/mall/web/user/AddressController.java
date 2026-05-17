package com.example.mall.web.user;

import com.example.mall.application.user.AddressService;
import com.example.mall.application.user.AddressService.AddressCommand;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@CurrentUser AuthenticatedUser principal) {
        return addressService.list(principal.id()).stream().map(AddressResponse::from).toList();
    }

    @PostMapping
    public AddressResponse create(
            @CurrentUser AuthenticatedUser principal, @Valid @RequestBody AddressRequest req) {
        return AddressResponse.from(addressService.create(principal.id(), toCmd(req)));
    }

    @PutMapping("/{id}")
    public AddressResponse update(
            @CurrentUser AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest req) {
        return AddressResponse.from(addressService.update(principal.id(), id, toCmd(req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser AuthenticatedUser principal, @PathVariable Long id) {
        addressService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    private static AddressCommand toCmd(AddressRequest req) {
        return new AddressCommand(
                req.recipient(),
                req.phone(),
                req.province(),
                req.city(),
                req.district(),
                req.detail(),
                req.isDefault());
    }
}
