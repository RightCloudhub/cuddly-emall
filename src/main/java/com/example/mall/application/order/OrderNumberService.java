package com.example.mall.application.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

/**
 * Mints order numbers shaped {@code MO\d{12}} — the format AskFlow's {@code search_order} tool
 * recognizes (regex {@code [A-Z]{2,4}\d{6,}}). The numeric portion is drawn from the Postgres
 * sequence {@code order_no_seq} so values are monotonically unique even under contention.
 */
@Service
public class OrderNumberService {

    @PersistenceContext private EntityManager em;

    public String next() {
        Number value =
                (Number) em.createNativeQuery("SELECT nextval('order_no_seq')").getSingleResult();
        return format(value.longValue());
    }

    public static String format(long sequenceValue) {
        if (sequenceValue < 0) {
            throw new IllegalArgumentException("order sequence must be non-negative");
        }
        if (sequenceValue > 999_999_999_999L) {
            throw new IllegalArgumentException(
                    "order sequence overflow — MO format supports only 12 digits");
        }
        return String.format("MO%012d", sequenceValue);
    }
}
