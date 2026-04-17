package com.kafka.order.consumer;

import com.kafka.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderConsumer {

    @KafkaListener(topics = "${kafka.topic.orders}", groupId = "${spring.kafka.consumer.group-id}")
    public void process(Order order) {
        log.info("Processing order: orderId={}, customer={}, product={} x{}, total={}",
                order.id(), order.customerId(), order.product(), order.quantity(), order.totalAmount());
        // Aqui entraria a lógica real: envio de e-mail, atualização de estoque, etc.
    }
}
