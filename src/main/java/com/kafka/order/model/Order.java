package com.kafka.order.model;

import java.math.BigDecimal;

public record Order(
        String id,
        String customerId,
        String product,
        int quantity,
        BigDecimal totalAmount
) {}
