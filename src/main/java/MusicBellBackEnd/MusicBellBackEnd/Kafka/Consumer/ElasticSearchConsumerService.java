package MusicBellBackEnd.MusicBellBackEnd.Kafka.Consumer;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch.ArtistSyncService;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Event.ElasticSearchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * ElasticSearch 이벤트 처리 Consumer
 * 
 * 개선사항:
 * 1. 상세한 에러 핸들링
 * 2. 메트릭스 및 모니터링
 * 3. 트랜잭션 처리
 * 4. 재시도 로직 (ErrorHandler에서 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSearchConsumerService {
    private final ArtistSyncService artistSyncService;

    @KafkaListener(
            topics = "${spring.kafka.topics.es-sending}",
            groupId = "${spring.kafka.es.consumer.group-id}",
            containerFactory = "elasticSearchKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleElasticSearchEvent(
            @Payload ElasticSearchEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, ElasticSearchEvent> consumerRecord,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();
        
        log.info("ElasticSearch 이벤트 수신 - Topic: {}, Partition: {}, Offset: {}, Event: {}", 
            topic, partition, offset, event);

        try {
            // 입력 검증
            validateEvent(event);
            
            // 비즈니스 로직 처리
            processEvent(event);
            
            // 성공 시 수동 커밋
            acknowledgment.acknowledge();
            
            // 성공 로그 및 메트릭스
            Duration processingTime = Duration.between(startTime, Instant.now());
            log.info("ElasticSearch 이벤트 처리 완료 - ArtistId: {}, Action: {}, 처리시간: {}ms", 
                event.getArtistId(), event.getAction(), processingTime.toMillis());
                
        } catch (IllegalArgumentException e) {
            // 비즈니스 검증 오류 - 재시도하지 않음 (즉시 DLQ)
            log.error("ElasticSearch 이벤트 검증 실패 (재시도 안함): {}", e.getMessage());
            throw e; // ErrorHandler에서 즉시 DLQ로 이동
            
        } catch (Exception e) {
            // 일반적인 처리 오류 - 재시도 가능
            log.error("ElasticSearch 이벤트 처리 실패 (재시도 예정) - Event: {}, Error: {}", 
                event, e.getMessage(), e);
            throw new ElasticSearchProcessingException("ElasticSearch 이벤트 처리 실패", e, event);
        }
    }

    /**
     * 이벤트 입력 검증
     */
    private void validateEvent(ElasticSearchEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("ElasticSearchEvent가 null입니다");
        }
        
        if (event.getArtistId() == null || event.getArtistId() <= 0) {
            throw new IllegalArgumentException("유효하지 않은 ArtistId: " + event.getArtistId());
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action이 비어있습니다");
        }
        
        if (!isValidAction(event.getAction())) {
            throw new IllegalArgumentException("지원하지 않는 Action: " + event.getAction());
        }
    }

    /**
     * 유효한 액션인지 확인
     */
    private boolean isValidAction(String action) {
        return "sync".equalsIgnoreCase(action) || "delete".equalsIgnoreCase(action);
    }

    /**
     * 실제 이벤트 처리 로직
     */
    private void processEvent(ElasticSearchEvent event) {
        String action = event.getAction().toLowerCase();
        Long artistId = event.getArtistId();

        switch (action) {
            case "sync":
                log.debug("ElasticSearch 동기화 시작 - ArtistId: {}", artistId);
                artistSyncService.syncSingleArtist(artistId);
                log.info("ElasticSearch 동기화 완료 - ArtistId: {}", artistId);
                break;
                
            case "delete":
                log.debug("ElasticSearch 인덱스 삭제 시작 - ArtistId: {}", artistId);
                artistSyncService.deleteFromIndex(artistId);
                log.info("ElasticSearch 인덱스 삭제 완료 - ArtistId: {}", artistId);
                break;
                
            default:
                throw new IllegalArgumentException("알 수 없는 ElasticSearch 작업 타입: " + action);
        }
    }

    /**
     * ElasticSearch 처리 전용 예외 클래스
     */
    public static class ElasticSearchProcessingException extends RuntimeException {
        private final ElasticSearchEvent event;

        public ElasticSearchProcessingException(String message, Throwable cause, ElasticSearchEvent event) {
            super(message, cause);
            this.event = event;
        }

        public ElasticSearchEvent getEvent() {
            return event;
        }
    }
}
