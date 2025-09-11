package MusicBellBackEnd.MusicBellBackEnd.Kafka.Config;

import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.DlqMessage;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정
 * 
 * 주요 기능:
 * 1. Consumer Factory 설정
 * 2. 에러 핸들링 적용
 * 3. 수동 커밋 모드 설정
 * 4. JSON 역직렬화 설정
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.es.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.dlq.consumer.group-id}")
    private String dlqGroupId;

    private final KafkaErrorHandlingConfig kafkaErrorHandlingConfig;

    /**
     * ElasticSearchEvent용 Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, ElasticSearchEvent> elasticSearchConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        // 기본 설정
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // JSON 역직렬화 설정 - 올바른 패키지명으로 수정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ElasticSearchEvent.class.getName());
        
        // 오프셋 관리
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // 성능 및 안정성 설정
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        
        // 재시도 설정
        configProps.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * ElasticSearchEvent용 Kafka Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ElasticSearchEvent> elasticSearchKafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        
        ConcurrentKafkaListenerContainerFactory<String, ElasticSearchEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(elasticSearchConsumerFactory());
        
        // 수동 커밋 모드 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // 동시성 설정 (파티션 수에 맞춰 조정)
        factory.setConcurrency(2);
        
        // 에러 핸들러 설정
        factory.setCommonErrorHandler(kafkaErrorHandlingConfig.kafkaErrorHandler(kafkaTemplate));
        
        return factory;
    }

    /**
     * DLQ 메시지용 Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, DlqMessage> dlqConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, dlqGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // JSON 역직렬화 설정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DlqMessage.class.getName());
        
        // 오프셋 관리
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * DLQ용 Kafka Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DlqMessage> dlqKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DlqMessage> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(dlqConsumerFactory());
        
        // 수동 커밋 모드 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // DLQ는 단일 스레드로 처리 (순서 보장)
        factory.setConcurrency(1);
        
        // DLQ는 에러 핸들러 없음 (최종 처리)
        
        return factory;
    }
}
