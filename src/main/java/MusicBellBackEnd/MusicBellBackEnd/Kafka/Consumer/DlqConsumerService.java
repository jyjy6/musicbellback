package MusicBellBackEnd.MusicBellBackEnd.Kafka.Consumer;

import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.DlqMessage;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * DLQ(Dead Letter Queue) 메시지 처리 Consumer
 * 
 * 기능:
 * 1. DLQ 메시지 수신 및 로깅
 * 2. 알림 발송 (관리자에게)
 * 3. 수동 재처리 지원
 * 4. 통계 및 모니터링
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlqConsumerService {

    @KafkaListener(
            topics = "${spring.kafka.topics.es-dlq:elasticsearch-dlq}",
            groupId = "${spring.kafka.dlq.consumer.group-id:dlq-consumer-group}",
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void handleDlqMessage(
            @Payload DlqMessage dlqMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.error("=== DLQ 메시지 수신 ===");
        log.error("Topic: {}, Partition: {}, Offset: {}", topic, partition, offset);
        log.error("DLQ 메시지 정보: {}", dlqMessage.getSummary());
        log.error("원본 메시지: {}", dlqMessage.getOriginalValue());
        log.error("에러 정보: {} - {}", dlqMessage.getErrorClass(), dlqMessage.getErrorMessage());
        log.error("실패 시간: {}", dlqMessage.getFailureDateTime());

        try {
            // DLQ 메시지 처리
            processDlqMessage(dlqMessage);
            
            // 성공 시 커밋
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 중 오류 발생", e);
            // DLQ 메시지 처리 실패 시에도 커밋 (무한 루프 방지)
            acknowledgment.acknowledge();
        }
    }

    /**
     * DLQ 메시지 처리 로직
     */
    private void processDlqMessage(DlqMessage dlqMessage) {
        // 1. 알림 발송
        sendAdminNotification(dlqMessage);
        
        // 2. 메트릭스 업데이트
        updateDlqMetrics(dlqMessage);
        
        // 3. 재처리 가능한 메시지인지 분석
        analyzeDlqMessage(dlqMessage);
    }

    /**
     * 관리자에게 DLQ 알림 발송
     */
    private void sendAdminNotification(DlqMessage dlqMessage) {
        try {
            // TODO: 실제 알림 시스템 연동 (이메일, 슬랙, 웹훅 등)
            log.warn("관리자 알림 발송 필요: DLQ 메시지 발생 - {}", dlqMessage.getSummary());
            
            // 심각한 오류의 경우 즉시 알림
            if (isCriticalError(dlqMessage)) {
                log.error("긴급 알림: 심각한 DLQ 메시지 발생 - {}", dlqMessage.getSummary());
            }
            
        } catch (Exception e) {
            log.error("DLQ 알림 발송 실패", e);
        }
    }

    /**
     * DLQ 메트릭스 업데이트
     */
    private void updateDlqMetrics(DlqMessage dlqMessage) {
        try {
            // TODO: 메트릭스 시스템 연동 (Micrometer, Prometheus 등)
            log.info("DLQ 메트릭스 업데이트: 토픽={}, 에러타입={}", 
                dlqMessage.getOriginalTopic(), dlqMessage.getErrorClass());
                
        } catch (Exception e) {
            log.error("DLQ 메트릭스 업데이트 실패", e);
        }
    }

    /**
     * DLQ 메시지 분석 및 재처리 가능성 판단
     */
    private void analyzeDlqMessage(DlqMessage dlqMessage) {
        try {
            Object originalValue = dlqMessage.getOriginalValue();
            String originalClassName = originalValue != null ? originalValue.getClass().getSimpleName() : "Unknown";
            
            log.info("DLQ 메시지 분석 시작 - 원본 타입: {}, 에러: {}", 
                originalClassName, dlqMessage.getErrorClass());
            
            // 이벤트 타입별 분석
            switch (originalClassName) {
                case "ElasticSearchEvent":
                    analyzeElasticSearchEvent(dlqMessage);
                    break;
                    
                // TODO: 다른 이벤트 타입들 추가
                // case "MusicEvent":
                //     analyzeMusicEvent(dlqMessage);
                //     break;
                // case "UserEvent":
                //     analyzeUserEvent(dlqMessage);
                //     break;
                    
                default:
                    analyzeUnknownEvent(dlqMessage, originalClassName);
                    break;
            }
            
        } catch (Exception e) {
            log.error("DLQ 메시지 분석 실패", e);
        }
    }
    
    /**
     * ElasticSearch 이벤트 분석
     */
    private void analyzeElasticSearchEvent(DlqMessage dlqMessage) {
        try {
            ElasticSearchEvent originalEvent = dlqMessage.getOriginalValueAs(ElasticSearchEvent.class);
            if (originalEvent == null) {
                log.warn("ElasticSearchEvent 캐스팅 실패");
                return;
            }
            
            log.info("ElasticSearch DLQ 분석 - ArtistId: {}, Action: {}, 에러: {}", 
                originalEvent.getArtistId(), 
                originalEvent.getAction(), 
                dlqMessage.getErrorClass());
            
            // 재처리 가능한 조건 확인
            if (isRetryableError(dlqMessage)) {
                log.warn("재처리 가능한 ElasticSearch DLQ 메시지: {}", dlqMessage.getSummary());
                // TODO: ElasticSearch 전용 재처리 로직
            }
            
            // ElasticSearch 특화 분석
            analyzeElasticSearchSpecific(originalEvent, dlqMessage);
            
        } catch (Exception e) {
            log.error("ElasticSearch 이벤트 분석 실패", e);
        }
    }
    
    /**
     * ElasticSearch 특화 분석
     */
    private void analyzeElasticSearchSpecific(ElasticSearchEvent event, DlqMessage dlqMessage) {
        // ArtistId 유효성 검사
        if (event.getArtistId() == null || event.getArtistId() <= 0) {
            log.error("유효하지 않은 ArtistId: {}", event.getArtistId());
        }
        
        // Action 유효성 검사
        if (!"sync".equals(event.getAction()) && !"delete".equals(event.getAction())) {
            log.error("지원하지 않는 Action: {}", event.getAction());
        }
        
        // TODO: ElasticSearch 서버 상태 확인
        // TODO: Artist 데이터 존재 여부 확인
    }
    
    /**
     * 알 수 없는 이벤트 타입 분석
     */
    private void analyzeUnknownEvent(DlqMessage dlqMessage, String eventType) {
        log.warn("알 수 없는 이벤트 타입 DLQ: {} - {}", eventType, dlqMessage.getSummary());
        
        // 기본적인 재처리 가능성 판단
        if (isRetryableError(dlqMessage)) {
            log.warn("재처리 가능한 알 수 없는 이벤트: {}", dlqMessage.getSummary());
        }
    }

    /**
     * 심각한 오류인지 판단
     */
    private boolean isCriticalError(DlqMessage dlqMessage) {
        String errorClass = dlqMessage.getErrorClass();
        
        // 시스템 레벨 오류들
        return "OutOfMemoryError".equals(errorClass) ||
               "StackOverflowError".equals(errorClass) ||
               "DatabaseConnectionException".equals(errorClass);
    }

    /**
     * 재처리 가능한 오류인지 판단
     */
    private boolean isRetryableError(DlqMessage dlqMessage) {
        String errorClass = dlqMessage.getErrorClass();
        String errorMessage = dlqMessage.getErrorMessage();
        
        // 일시적 오류들 (네트워크, 타임아웃 등)
        if ("ConnectTimeoutException".equals(errorClass) ||
            "SocketTimeoutException".equals(errorClass) ||
            "ConnectionException".equals(errorClass)) {
            return true;
        }
        
        // 에러 메시지 기반 판단
        if (errorMessage != null) {
            String lowerMessage = errorMessage.toLowerCase();
            return lowerMessage.contains("timeout") ||
                   lowerMessage.contains("connection") ||
                   lowerMessage.contains("network") ||
                   lowerMessage.contains("unavailable");
        }
        
        return false;
    }

    /**
     * 수동 재처리 메서드 (관리자용)
     */
    public void manualRetryDlqMessage(DlqMessage dlqMessage) {
        log.info("DLQ 메시지 수동 재처리 시작: {}", dlqMessage.getSummary());
        
        try {
            // TODO: 원본 토픽으로 메시지 재전송 로직 구현
            log.info("DLQ 메시지 수동 재처리 완료: {}", dlqMessage.getSummary());
            
        } catch (Exception e) {
            log.error("DLQ 메시지 수동 재처리 실패", e);
            throw e;
        }
    }
}
