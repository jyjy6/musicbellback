package MusicBellBackEnd.MusicBellBackEnd.Kafka.Config;

import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.DlqMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

/**
 * Kafka Consumer 에러 핸들링 및 재시도 설정
 * 
 * 주요 기능:
 * 1. 재시도 메커니즘 (Exponential Backoff)
 * 2. DLQ (Dead Letter Queue) 처리
 * 3. 에러 로깅 및 모니터링
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Value("${spring.kafka.topics.es-sending}")
    private String originalTopic;

    @Value("${spring.kafka.topics.es-retry}")
    private String retryTopic;

    @Value("${spring.kafka.topics.es-dlq}")
    private String dlqTopic;

    /**
     * 에러 핸들러 설정
     * - 최대 3회 재시도
     * - Exponential Backoff: 1초 → 2초 → 4초
     * - 최종 실패 시 DLQ로 이동
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("kafkaErrorHandler 작동");
        // Exponential Backoff 설정
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);      // 첫 번째 재시도: 1초 후
        backOff.setMultiplier(2.0);             // 배수: 2배씩 증가
        backOff.setMaxInterval(10000L);         // 최대 대기시간: 10초
        backOff.setMaxElapsedTime(30000L);      // 전체 재시도 시간: 30초

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            // DLQ로 메시지 전송하는 복구 함수
            (consumerRecord, exception) -> {
                handleDlqMessage(consumerRecord, exception, kafkaTemplate);
            },
            backOff
        );

        // 재시도하지 않을 예외 타입 설정 (즉시 DLQ로 이동)
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            NullPointerException.class
        );

        // 에러 발생 시 로깅
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("메시지 처리 재시도 - 시도 횟수: {}, 토픽: {}, 파티션: {}, 오프셋: {}, 에러: {}", 
                deliveryAttempt, 
                record.topic(), 
                record.partition(), 
                record.offset(), 
                ex.getMessage());
        });

        return errorHandler;
    }

    /**
     * DLQ로 메시지 전송 처리
     */
    private void handleDlqMessage(ConsumerRecord<?, ?> consumerRecord, Exception exception, 
                                 KafkaTemplate<String, Object> kafkaTemplate) {
        try {
            log.error("메시지를 DLQ로 이동 - 토픽: {}, 파티션: {}, 오프셋: {}, 에러: {}", 
                consumerRecord.topic(), 
                consumerRecord.partition(), 
                consumerRecord.offset(), 
                exception.getMessage());

            // DLQ 메시지 생성 (원본 메시지 + 에러 정보)
            DlqMessage dlqMessage = DlqMessage.builder()
                .originalTopic(consumerRecord.topic())
                .originalPartition(consumerRecord.partition())
                .originalOffset(consumerRecord.offset())
                .originalKey(consumerRecord.key() != null ? consumerRecord.key().toString() : null)
                .originalValue(consumerRecord.value())
                .errorMessage(exception.getMessage())
                .errorClass(exception.getClass().getSimpleName())
                .failureTimestamp(System.currentTimeMillis())
                .build();

            // DLQ 토픽으로 전송
            kafkaTemplate.send(dlqTopic, dlqMessage);
            
            log.info("DLQ 메시지 전송 완료: {}", dlqMessage);

        } catch (Exception e) {
            log.error("DLQ 메시지 전송 실패", e);
        }
    }
}
