package com.example.mall.application.shipment;

import com.example.mall.domain.shipment.Shipment;
import com.example.mall.domain.shipment.ShipmentRepository;
import com.example.mall.web.error.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    @PersistenceContext private EntityManager em;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    /**
     * Create a pending shipment for the order with a synthetic carrier tracking number of the form
     * {@code SF\d{10}}. Throws {@link ConflictException} if a shipment already exists.
     */
    @Transactional
    public Shipment createForOrder(Long orderId) {
        shipmentRepository
                .findByOrderId(orderId)
                .ifPresent(
                        s -> {
                            throw new ConflictException(
                                    "shipment already exists for order " + orderId);
                        });
        String tracking = nextTrackingNo();
        return shipmentRepository.save(new Shipment(orderId, tracking, "SF"));
    }

    private String nextTrackingNo() {
        Number value =
                (Number)
                        em.createNativeQuery("SELECT nextval('shipment_tracking_seq')")
                                .getSingleResult();
        long v = value.longValue();
        if (v > 9_999_999_999L) {
            throw new IllegalStateException("shipment tracking sequence exceeded 10 digits");
        }
        return String.format("SF%010d", v);
    }
}
