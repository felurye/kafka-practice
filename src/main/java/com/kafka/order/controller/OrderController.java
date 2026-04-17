package com.kafka.order.controller;

import com.kafka.order.model.Order;
import com.kafka.order.model.PlaceOrderRequest;
import com.kafka.order.producer.OrderProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProducer orderProducer;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestBody PlaceOrderRequest request) {
        Order order = new Order(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.product(),
                request.quantity(),
                request.totalAmount()
        );
        orderProducer.publish(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
