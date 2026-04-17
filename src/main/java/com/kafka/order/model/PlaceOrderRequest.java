package com.kafka.order.model;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        String customerId,
        String product,
        int quantity,
        BigDecimal totalAmount
) {}
