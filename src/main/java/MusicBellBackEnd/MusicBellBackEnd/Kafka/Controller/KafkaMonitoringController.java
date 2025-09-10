package MusicBellBackEnd.MusicBellBackEnd.Kafka.Controller;

import MusicBellBackEnd.MusicBellBackEnd.Kafka.Consumer.DlqConsumerService;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.DlqMessage;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Producer.ElasticSearchProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaMonitoringController {

    private final ElasticSearchProducerService producerService;
    private final DlqConsumerService dlqConsumerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.es-sending}")
    private String originalTopic;

    @Value("${spring.kafka.topics.es-dlq}")
    private String dlqTopic;

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
                    break;
                case "runtime":
                    // 런타임 오류 (재시도 후 DLQ)
                    testEvent = new ElasticSearchEvent(-999L, "sync");
                    break;
                case "null":
                    // NPE 테스트 (즉시 DLQ로 이동)
                    testEvent = new ElasticSearchEvent(artistId, null);
                    break;
                default:
                    testEvent = new ElasticSearchEvent(artistId, "sync");
            }
            
            kafkaTemplate.send(originalTopic, testEvent);
            
            response.put("success", true);
            response.put("message", "에러 테스트 메시지 발송 완료");
            response.put("errorType", errorType);
            response.put("testEvent", testEvent);
            
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
            
            // TODO: 실제 Kafka 클러스터 상태 확인 로직 추가
            
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
}
