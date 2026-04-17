package com.kafka.order.producer;

import com.kafka.order.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProducer {

    @Value("${kafka.topic.orders}")
    private String ordersTopic;

    private final KafkaTemplate<String, Order> kafkaTemplate;

    public void publish(Order order) {
        kafkaTemplate.send(ordersTopic, order.id(), order);
        log.info("Order published: orderId={}, customerId={}", order.id(), order.customerId());
    }
}
