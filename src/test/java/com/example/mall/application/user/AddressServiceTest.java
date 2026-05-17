package com.example.mall.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mall.application.user.AddressService.AddressCommand;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock AddressRepository repo;
    AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(repo);
    }

    @Test
    void createDefaultAddressClearsExistingDefault() {
        AddressCommand cmd =
                new AddressCommand("Alice", "13800000000", "P", "C", "D", "detail", true);
        Mockito.when(repo.save(Mockito.any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(7L, cmd);

        Mockito.verify(repo).clearDefaultForUser(7L);
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        Mockito.verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void createNonDefaultDoesNotClearOthers() {
        AddressCommand cmd =
                new AddressCommand("Alice", "13800000000", "P", "C", "D", "detail", false);
        Mockito.when(repo.save(Mockito.any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(7L, cmd);

        Mockito.verify(repo, Mockito.never()).clearDefaultForUser(Mockito.anyLong());
    }

    @Test
    void updateRejectsAddressOwnedByAnotherUser() {
        Mockito.when(repo.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        AddressCommand cmd =
                new AddressCommand("Alice", "13800000000", "P", "C", "D", "detail", false);
        assertThatThrownBy(() -> service.update(7L, 99L, cmd))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRejectsAddressOwnedByAnotherUser() {
        Mockito.when(repo.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(NotFoundException.class);
        Mockito.verify(repo, Mockito.never()).delete(Mockito.any());
    }

    @Test
    void listReturnsForUserOnly() {
        Mockito.when(repo.findByUserIdOrderByIsDefaultDescIdAsc(7L)).thenReturn(List.of());
        assertThat(service.list(7L)).isEmpty();
    }
}
