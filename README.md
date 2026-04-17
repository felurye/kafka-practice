# Kafka Practice

Projeto de estudo com **Spring Boot** e **Apache Kafka**, demonstrando o padrão producer/consumer em um cenário de **notificação de pedidos** (e-commerce).

Quando um pedido é feito via REST, o producer publica o evento no tópico `orders`. O consumer escuta o tópico e processa a notificação - ponto de extensão para envio de e-mail, atualização de estoque, etc.

## Stack

- Java 21
- Spring Boot 3.3.4
- Spring Kafka
- Docker Compose (Bitnami Kafka 3.7 em modo KRaft + Kafka UI)
- Lombok

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose

## Como executar

**1. Subir a infraestrutura Kafka:**

```bash
docker compose up -d
```

| Serviço   | Porta |
|-----------|-------|
| Kafka     | 9092  |
| Kafka UI  | 8080  |

> O Zookeeper foi removido. O Kafka 3.7 roda em modo **KRaft** (coordenação nativa, sem dependência externa).

**2. Iniciar a aplicação:**

```bash
./mvnw spring-boot:run
```

A aplicação sobe na porta `8081`.

## API

### Registrar um pedido

```
POST /orders
Content-Type: application/json
```

**Body:**

```json
{
  "customerId": "customer-42",
  "product": "Teclado Mecânico",
  "quantity": 1,
  "totalAmount": 349.90
}
```

**Exemplo com curl:**

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-42","product":"Teclado Mecânico","quantity":1,"totalAmount":349.90}'
```

**Resposta:** `201 Created`

```json
{
  "id": "3f1b2c4d-...",
  "customerId": "customer-42",
  "product": "Teclado Mecânico",
  "quantity": 1,
  "totalAmount": 349.90
}
```

O pedido é publicado no tópico `orders` com o `id` como chave da mensagem. O consumer recebe o evento e loga os detalhes - simulando o processamento de notificação.

## Arquitetura

```
REST Client
    |
    v
OrderController       POST /orders
    |
    v
OrderProducer         KafkaTemplate -> tópico "orders" (chave: orderId)
    |
    v   [Kafka broker]
OrderConsumer         @KafkaListener -> processa o pedido (log + extensível)
```

## Monitoramento

O **Kafka UI** fica disponível em `http://localhost:8080` e permite inspecionar tópicos, mensagens e grupos de consumidores.

O **Actuator** expõe endpoints de saúde em `http://localhost:8081/actuator/health`.
