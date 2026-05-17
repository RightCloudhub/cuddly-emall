package com.example.mall.domain.promotion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findByUserIdAndUsedAtIsNull(Long userId);
}
