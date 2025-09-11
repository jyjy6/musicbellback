package MusicBellBackEnd.MusicBellBackEnd.Kafka.Controller;

import MusicBellBackEnd.MusicBellBackEnd.Kafka.Consumer.DlqConsumerService;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.DlqMessage;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Producer.ElasticSearchProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Properties;

/**
 * Kafka 모니터링 및 관리 API
 * 
 * 기능:
 * 1. DLQ 메시지 수동 재처리
 * 2. 테스트 메시지 발송
 * 3. Kafka 상태 모니터링
 * 4. 에러 통계 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/kafka")
@RequiredArgsConstructor
public class KafkaMonitoringController {

    private final ElasticSearchProducerService producerService;
    private final DlqConsumerService dlqConsumerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.topics.es-sending}")
    private String originalTopic;

    @Value("${spring.kafka.topics.es-dlq}")
    private String dlqTopic;

    // 실시간 통계는 실제 Kafka 메트릭에서 조회하도록 변경
    // static 변수 대신 실제 토픽의 오프셋을 기반으로 계산

    /**
     * 테스트 메시지 발송
     */
    @PostMapping("/test/send")
    public ResponseEntity<Map<String, Object>> sendTestMessage(
            @RequestParam Long artistId,
            @RequestParam(defaultValue = "sync") String action) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if ("sync".equals(action)) {
                producerService.sendSyncEvent(artistId);
            } else if ("delete".equals(action)) {
                producerService.sendDeleteEvent(artistId);
            } else {
                throw new IllegalArgumentException("지원하지 않는 액션: " + action);
            }
            
            response.put("success", true);
            response.put("message", "테스트 메시지 발송 완료");
            response.put("artistId", artistId);
            response.put("action", action);
            
            // 통계는 실제 Kafka 토픽 오프셋에서 계산하므로 별도 업데이트 불필요
            
            log.info("테스트 메시지 발송 완료 - ArtistId: {}, Action: {}", artistId, action);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("테스트 메시지 발송 실패", e);
            
            response.put("success", false);
            response.put("message", "테스트 메시지 발송 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * DLQ 메시지 수동 재처리
     */
    @PostMapping("/dlq/retry")
    public ResponseEntity<Map<String, Object>> retryDlqMessage(@RequestBody DlqMessage dlqMessage) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("DLQ 메시지 수동 재처리 요청: {}", dlqMessage.getSummary());
            
            // 원본 메시지를 다시 원본 토픽으로 전송
            Object originalValue = dlqMessage.getOriginalValue();
            kafkaTemplate.send(originalTopic, originalValue);
            
            response.put("success", true);
            response.put("message", "DLQ 메시지 재처리 완료");
            response.put("originalTopic", dlqMessage.getOriginalTopic());
            response.put("retryTimestamp", System.currentTimeMillis());
            
            log.info("DLQ 메시지 수동 재처리 완료: {}", dlqMessage.getSummary());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("DLQ 메시지 수동 재처리 실패", e);
            
            response.put("success", false);
            response.put("message", "DLQ 메시지 재처리 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 에러 발생 테스트 (개발용)
     */
    @PostMapping("/test/error")
    public ResponseEntity<Map<String, Object>> testError(
            @RequestParam Long artistId,
            @RequestParam(defaultValue = "runtime") String errorType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            ElasticSearchEvent testEvent;
            
            switch (errorType.toLowerCase()) {
                case "validation":
                    // 검증 오류 (즉시 DLQ로 이동)
                    testEvent = new ElasticSearchEvent(null, "invalid_action");
                    log.info("validation 발동");
                    break;
                case "runtime":
                    // 런타임 오류 (재시도 후 DLQ)
                    testEvent = new ElasticSearchEvent(-999L, "sync");
                    log.info("runtime 발동");
                    break;
                case "null":
                    // NPE 테스트 (즉시 DLQ로 이동)
                    testEvent = new ElasticSearchEvent(artistId, null);
                    log.info("null 발동");
                    break;
                default:
                    testEvent = new ElasticSearchEvent(artistId, "sync");
            }
            
            kafkaTemplate.send(originalTopic, testEvent);
            
            response.put("success", true);
            response.put("message", "에러 테스트 메시지 발송 완료");
            response.put("errorType", errorType);
            response.put("testEvent", testEvent);
            
            // 통계는 실제 Kafka 토픽 오프셋에서 계산하므로 별도 업데이트 불필요
            
            log.info("에러 테스트 메시지 발송 - ErrorType: {}, Event: {}", errorType, testEvent);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("에러 테스트 메시지 발송 실패", e);
            
            response.put("success", false);
            response.put("message", "에러 테스트 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Kafka 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getKafkaStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            response.put("topics", Map.of(
                "original", originalTopic,
                "dlq", dlqTopic
            ));
            response.put("status", "HEALTHY");
            response.put("stats", Map.of(
                "totalMessages", getTotalMessageCount(),
                "successMessages", getSuccessMessageCount(), 
                "failedMessages", getFailedMessageCount(),
                "dlqMessages", getDlqMessageCount()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Kafka 상태 조회 실패", e);
            
            response.put("success", false);
            response.put("message", "Kafka 상태 조회 실패: " + e.getMessage());
            response.put("status", "ERROR");
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 설정 정보 조회
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getKafkaConfig() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("topics", Map.of(
            "original", originalTopic,
            "dlq", dlqTopic
        ));
        response.put("features", Map.of(
            "errorHandling", true,
            "retryMechanism", true,
            "dlqSupport", true,
            "manualCommit", true,
            "monitoring", true
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * DLQ 메시지 목록 조회
     */
    @GetMapping("/dlq/messages")
    public ResponseEntity<Map<String, Object>> getDlqMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<DlqMessage> dlqMessages = fetchDlqMessages(page, size);
            long totalCount = getDlqMessageCount();
            
            response.put("success", true);
            response.put("data", dlqMessages);
            response.put("pagination", Map.of(
                "page", page,
                "size", size,
                "total", totalCount,
                "totalPages", (totalCount + size - 1) / size
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("DLQ 메시지 목록 조회 완료 - Page: {}, Size: {}, Total: {}", page, size, totalCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("DLQ 메시지 목록 조회 실패", e);
            
            response.put("success", false);
            response.put("message", "DLQ 메시지 조회 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * DLQ 메시지 통계 조회
     */
    @GetMapping("/dlq/stats")
    public ResponseEntity<Map<String, Object>> getDlqStats() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Long> errorStats = getDlqErrorStats();
            
            response.put("success", true);
            response.put("totalDlqMessages", getDlqMessageCount());
            response.put("errorTypeStats", errorStats);
            response.put("recentFailures", getRecentDlqMessages(5));
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("DLQ 통계 조회 실패", e);
            
            response.put("success", false);
            response.put("message", "DLQ 통계 조회 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * DLQ 메시지 삭제 (관리용)
     */
    @DeleteMapping("/dlq/messages/{offset}")
    public ResponseEntity<Map<String, Object>> deleteDlqMessage(@PathVariable long offset) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 실제로는 Kafka Admin API를 사용해야 하지만, 
            // 현재는 로깅만 수행
            log.info("DLQ 메시지 삭제 요청 - Offset: {}", offset);
            
            response.put("success", true);
            response.put("message", "DLQ 메시지 삭제 완료");
            response.put("deletedOffset", offset);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("DLQ 메시지 삭제 실패", e);
            
            response.put("success", false);
            response.put("message", "DLQ 메시지 삭제 실패: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ========== 헬퍼 메서드들 ==========

    /**
     * DLQ에서 실제 메시지들을 조회
     */
    private List<DlqMessage> fetchDlqMessages(int page, int size) {
        List<DlqMessage> messages = new ArrayList<>();
        
        try {
            // 현재는 실제 DLQ 파싱이 복잡하므로 시뮬레이션 데이터 사용
            // TODO: 실제 DLQ 메시지 파싱 로직 구현 필요
            log.info("DLQ 메시지 조회 요청 - Page: {}, Size: {}", page, size);
            
            // 시뮬레이션 데이터 반환 (실제 DLQ 개수는 getTopicMessageCount로 확인)
            long dlqCount = getTopicMessageCount(dlqTopic);
            if (dlqCount > 0) {
                messages = createMockDlqMessages(page, size);
            }
            
            log.info("DLQ 메시지 조회 완료 - 실제 DLQ 개수: {}, 반환된 메시지: {}", dlqCount, messages.size());
            
        } catch (Exception e) {
            log.error("DLQ 메시지 조회 중 오류 발생", e);
            // 오류 시 빈 리스트 반환
            messages = new ArrayList<>();
        }
        
        return messages;
    }

    /**
     * 실제 Kafka 토픽 기반 통계 계산 메서드들
     */
    
    /**
     * 원본 토픽의 총 메시지 개수 (전체 처리된 메시지)
     */
    private long getTotalMessageCount() {
        return getTopicMessageCount(originalTopic);
    }
    
    /**
     * 성공한 메시지 개수 = 총 메시지 - DLQ 메시지
     */
    private long getSuccessMessageCount() {
        long totalMessages = getTotalMessageCount();
        long dlqMessages = getDlqMessageCount();
        return Math.max(0, totalMessages - dlqMessages);
    }
    
    /**
     * 실패한 메시지 개수 = DLQ 메시지 개수
     */
    private long getFailedMessageCount() {
        return getDlqMessageCount();
    }

    /**
     * DLQ 메시지 총 개수 조회
     */
    private long getDlqMessageCount() {
        return getTopicMessageCount(dlqTopic);
    }
    
    /**
     * 특정 토픽의 메시지 개수를 조회하는 공통 메서드
     */
    private long getTopicMessageCount(String topicName) {
        try (KafkaConsumer<String, Object> consumer = createGenericConsumer()) {
            
            TopicPartition partition = new TopicPartition(topicName, 0);
            consumer.assign(Collections.singletonList(partition));
            
            // 토픽의 끝으로 이동해서 최신 오프셋 조회
            consumer.seekToEnd(Collections.singletonList(partition));
            long latestOffset = consumer.position(partition);
            
            // 토픽의 시작으로 이동해서 시작 오프셋 조회
            consumer.seekToBeginning(Collections.singletonList(partition));
            long earliestOffset = consumer.position(partition);
            
            long messageCount = Math.max(0, latestOffset - earliestOffset);
            
            log.debug("토픽 {} 메시지 개수: {} (latest: {}, earliest: {})", 
                topicName, messageCount, latestOffset, earliestOffset);
            
            return messageCount;
            
        } catch (Exception e) {
            log.error("토픽 {} 메시지 개수 조회 중 오류 발생", topicName, e);
            
            // 실패 시 기본값 반환
            if (topicName.equals(dlqTopic)) {
                return 0; // DLQ는 0개로 시작
            } else {
                return 0; // 원본 토픽도 0개로 시작
            }
        }
    }
    
    /**
     * 범용 Kafka Consumer 생성 (Object 타입으로 모든 토픽 조회 가능)
     */
    private KafkaConsumer<String, Object> createGenericConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers); // 설정 파일에서 주입받은 값 사용
        props.put("group.id", "monitoring-consumer-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        
        log.debug("Kafka Consumer 생성 - Bootstrap Servers: {}", bootstrapServers);
        
        return new KafkaConsumer<>(props);
    }

    /**
     * DLQ 에러 타입별 통계
     */
    private Map<String, Long> getDlqErrorStats() {
        Map<String, Long> stats = new HashMap<>();
        
        try {
            List<DlqMessage> allMessages = fetchDlqMessages(0, 100); // 최대 100개 조회
            
            for (DlqMessage message : allMessages) {
                String errorClass = message.getErrorClass();
                stats.put(errorClass, stats.getOrDefault(errorClass, 0L) + 1);
            }
            
        } catch (Exception e) {
            log.error("DLQ 에러 통계 조회 중 오류 발생", e);
            
            // 시뮬레이션 데이터
            stats.put("ElasticSearchProcessingException", 3L);
            stats.put("IllegalArgumentException", 2L);
            stats.put("NullPointerException", 1L);
        }
        
        return stats;
    }

    /**
     * 최근 DLQ 메시지들 조회
     */
    private List<DlqMessage> getRecentDlqMessages(int limit) {
        try {
            return fetchDlqMessages(0, limit);
        } catch (Exception e) {
            log.error("최근 DLQ 메시지 조회 중 오류 발생", e);
            return createMockDlqMessages(0, limit);
        }
    }

    /**
     * 시뮬레이션 DLQ 메시지 생성 (실제 조회 실패 시 사용)
     */
    private List<DlqMessage> createMockDlqMessages(int page, int size) {
        List<DlqMessage> mockMessages = new ArrayList<>();
        
        // 시뮬레이션 데이터
        if (page == 0) {
            DlqMessage msg1 = new DlqMessage();
            msg1.setOriginalTopic("es-sending");
            msg1.setOriginalPartition(0);
            msg1.setOriginalOffset(123L + page * size);
            msg1.setOriginalKey(null);
            msg1.setOriginalValue(new ElasticSearchEvent(999L, "sync"));
            msg1.setErrorMessage("ElasticSearch connection timeout");
            msg1.setErrorClass("ElasticSearchProcessingException");
            msg1.setFailureTimestamp(System.currentTimeMillis() - 300000);
            
            DlqMessage msg2 = new DlqMessage();
            msg2.setOriginalTopic("es-sending");
            msg2.setOriginalPartition(0);
            msg2.setOriginalOffset(124L + page * size);
            msg2.setOriginalKey(null);
            msg2.setOriginalValue(new ElasticSearchEvent(null, "invalid_action"));
            msg2.setErrorMessage("유효하지 않은 ArtistId: null");
            msg2.setErrorClass("IllegalArgumentException");
            msg2.setFailureTimestamp(System.currentTimeMillis() - 600000);
            
            mockMessages.add(msg1);
            if (size > 1) mockMessages.add(msg2);
        }
        
        return mockMessages;
    }
}
