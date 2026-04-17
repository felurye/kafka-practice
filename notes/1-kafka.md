# Kafka Overview

## Diferença do Kafka para outras plataformas de Streaming

|                  Sistemas de Mensageria Tradicional                  |                                     Kafka                                     |
| :------------------------------------------------------------------: | :---------------------------------------------------------------------------: |
|                         Não são distribuídos                         |                   É uma plataforma de streaming distribuída                   |
|                    Transient Message Persistence                     |   Armazena eventos com base em retenção de tempo. Os eventos são imutáveis    |
| O broker rastreia quais mensagens foram consumidas por cada consumer | Responsabilidade do consumidor manter o rastreamento das mensagens consumidas |
|           Projetado para atingir um consumidor específico            |           Qualquer consumidor pode acessar uma mensagem do producer           |

## Brokers

Um **broker** é um servidor Kafka - uma instância do processo Kafka em execução. Ele é responsável por:

- Receber e armazenar mensagens enviadas pelos producers.
- Servir mensagens para os consumers.
- Gerenciar as partições dos tópicos.

Um cluster Kafka é formado por um ou mais brokers. A distribuição de partições entre os brokers garante tolerância a falhas e escalabilidade horizontal.

Cada broker é identificado por um `node.id` inteiro. No `docker-compose.yml` deste projeto, o broker tem `KAFKA_CFG_NODE_ID=0` e opera com `KAFKA_CFG_PROCESS_ROLES=controller,broker` (modo KRaft).

### KRaft (Kafka sem ZooKeeper)

A partir do Kafka 3.3+, o **ZooKeeper foi substituído pelo modo KRaft** (Kafka Raft Metadata). Nesse modelo:

- O próprio Kafka gerencia seus metadados internamente via protocolo Raft.
- Um ou mais nós assumem o papel de `controller`, responsável pela eleição de líderes de partição e gerenciamento do cluster.
- Elimina a dependência de um serviço externo, simplificando o deploy.

No `docker-compose.yml` deste projeto, o nó age como `controller` e `broker` simultaneamente, escutando na porta `9093` para comunicação interna do controller.

#### ZooKeeper (legado)

Antes do KRaft, o ZooKeeper era obrigatório. Ele atuava como serviço de coordenação distribuída, armazenando metadados do cluster (lista de brokers ativos, configurações de tópicos, offsets de consumers, eleição de controller). Ainda pode ser encontrado em clusters legados.

## Producer

O **producer** é o componente responsável por publicar mensagens em um tópico Kafka. Pontos importantes:

- Envia mensagens para uma partição específica, determinada pela **chave da mensagem** (`key`). Mensagens com a mesma chave sempre vão para a mesma partição (garantia de ordenação por chave).
- Se nenhuma chave for fornecida, o producer distribui as mensagens entre partições via round-robin ou sticky partitioner.
- A entrega pode ser configurada com diferentes garantias de durabilidade via `acks`:
  - `acks=0` - sem confirmação (fire and forget).
  - `acks=1` - confirma quando o líder da partição grava.
  - `acks=all` - confirma quando todos as réplicas gravam.

### Implementação neste projeto

```java
// OrderProducer.java
kafkaTemplate.send(ordersTopic, order.id(), order);
//                              ^^^^^^^^^
//                              chave = id do pedido (garante ordenação por pedido)
```

O `KafkaTemplate<String, Order>` usa:

- `StringSerializer` para serializar a chave.
- `JsonSerializer` para serializar o valor (`Order`).

Configuração em `application.yml`:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

## Consumer

O **consumer** consome mensagens de um ou mais tópicos. Ele faz **poll** ativo no broker - o broker não faz push.

- Cada consumer pertence a um **consumer group** (identificado por `group-id`).
- Dentro de um grupo, cada partição é consumida por apenas um consumer. Isso garante que a mesma mensagem não seja processada duas vezes pelo mesmo grupo.
- Grupos diferentes podem consumir o mesmo tópico independentemente (broadcast por grupo).
- O consumer mantém o **offset** - a posição da última mensagem lida em cada partição. O offset é commitado no Kafka (tópico interno `__consumer_offsets`).

### `auto-offset-reset`

Define o comportamento quando um consumer inicia e não encontra offset commitado:

- `earliest` - começa do início da partição (padrão neste projeto).
- `latest` - começa apenas nas mensagens novas.

### Implementação neste projeto

```java
// OrderConsumer.java
@KafkaListener(topics = "${kafka.topic.orders}", groupId = "${spring.kafka.consumer.group-id}")
public void process(Order order) { ... }
```

O `JsonDeserializer` reconstrói o objeto `Order` a partir do JSON. A propriedade `spring.json.trusted.packages` restringe quais pacotes podem ser desserializados (proteção contra ataques de desserialização).

## Tópicos

Um **tópico** é um canal nomeado onde os producers publicam e os consumers leem mensagens. É análogo a uma tabela de banco de dados ou uma fila, porém com diferenças importantes:

- As mensagens **não são deletadas após o consumo** - elas persistem pelo período de retenção configurado.
- Múltiplos consumers (de grupos diferentes) podem ler o mesmo tópico.

### Partições

Cada tópico é dividido em uma ou mais **partições**. As partições são a unidade fundamental de paralelismo e escalabilidade do Kafka:

- Cada partição é uma sequência ordenada e imutável de mensagens.
- Partições de um mesmo tópico podem ser distribuídas em brokers diferentes.
- O número de partições determina o grau máximo de paralelismo para consumo - você não pode ter mais consumers ativos (no mesmo grupo) do que partições.
- A ordenação é garantida **dentro de uma partição**, não entre partições.

### Réplicas

Cada partição pode ter **N réplicas** distribuídas em brokers diferentes. Uma réplica é o **líder** (recebe leituras e escritas) e as demais são **followers** (replicam do líder). Se o líder cair, um follower assume.

No `KafkaTopicConfig.java` deste projeto:

```java
TopicBuilder.name(ordersTopic)
    .partitions(1)
    .replicas(1)   // ambiente local, sem necessidade de redundância
    .build();
```

## DLQ (Dead Letter Queue)

A **DLQ** (fila de mensagens mortas) é um tópico especial usado para armazenar mensagens que **não puderam ser processadas** após N tentativas.

Quando um consumer lança uma exceção ao processar uma mensagem, o Kafka (com Spring Kafka) pode ser configurado para:

1. Tentar reprocessar N vezes (retry com backoff).
2. Se ainda falhar, publicar a mensagem em um tópico de DLQ (ex: `orders.DLT`).
3. Isso evita que uma mensagem problemática bloqueie o processamento da fila inteira.

Configuração típica com Spring Kafka:

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    var backoff = new FixedBackOff(1000L, 3); // 3 tentativas, 1s de intervalo
    return new DefaultErrorHandler(recoverer, backoff);
}
```

## Ciclo de uma mensagem

```
[Producer]
    |
    | send(topic, key, value)
    v
[Broker / Partição líder]
    |
    | armazena na partição (append-only, offset N)
    | replica para followers (se replicas > 1)
    |
    v
[Consumer - poll()]
    |
    | recebe a mensagem (offset N)
    | processa
    | commita offset N+1
    v
  [Fim]
    |
    | se falha no processamento:
    v
[DLQ / Retry]
```

Passos detalhados:

1. **Produção** - o producer serializa a mensagem e a envia ao broker líder da partição correspondente à chave.
2. **Armazenamento** - o broker grava a mensagem no log da partição e atribui um offset sequencial.
3. **Replicação** - followers sincronizam a mensagem do líder (conforme `acks` configurado).
4. **Consumo** - o consumer faz poll e recebe um lote de mensagens a partir do último offset commitado.
5. **Processamento** - a aplicação processa a mensagem (ex: salvar no banco, enviar e-mail).
6. **Commit de offset** - o consumer commita o offset, sinalizando que a mensagem foi processada com sucesso.
7. **Falha** - se o processamento falhar, o offset não é commitado e a mensagem pode ser reprocessada ou encaminhada para a DLQ.

## Infraestrutura deste projeto

Sobe via `docker compose up -d`:

| Serviço    | Imagem                   | Porta  | Descrição                                |
| :--------- | :----------------------- | :----: | :--------------------------------------- |
| `kafka`    | `bitnami/kafka:3.7`      | `9092` | Broker + Controller (modo KRaft)         |
| `kafka-ui` | `provectuslabs/kafka-ui` | `8080` | Interface web para inspecionar o cluster |

A aplicação Spring Boot sobe na porta `8081` e se conecta ao broker em `127.0.0.1:9092`.
