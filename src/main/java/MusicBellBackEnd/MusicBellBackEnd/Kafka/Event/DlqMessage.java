package MusicBellBackEnd.MusicBellBackEnd.Kafka.Event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * DLQ(Dead Letter Queue)로 전송되는 메시지 모델
 * 
 * 원본 메시지 정보와 에러 정보를 포함하여
 * 나중에 문제 분석 및 재처리가 가능하도록 구성
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage {
    
    // 원본 메시지 정보
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private String originalKey;
    private Object originalValue;
    
    // 에러 정보
    private String errorMessage;
    private String errorClass;
    private String stackTrace;
    
    // 시간 정보
    private Long failureTimestamp;
    private LocalDateTime failureDateTime;
    
    // 재처리 정보
    private Integer retryCount;
    private String processingStatus; // "FAILED", "RETRY_EXHAUSTED", "MANUAL_REVIEW"
    
    // 추가 메타데이터
    private String consumerGroup;
    private String applicationVersion;
    
    /**
     * 실패 시간을 LocalDateTime으로 변환하여 반환
     */
    public LocalDateTime getFailureDateTime() {
        if (failureDateTime == null && failureTimestamp != null) {
            this.failureDateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(failureTimestamp),
                ZoneId.systemDefault()
            );
        }
        return failureDateTime;
    }
    
    /**
     * 원본 메시지를 특정 타입으로 캐스팅
     */
    @SuppressWarnings("unchecked")
    public <T> T getOriginalValueAs(Class<T> clazz) {
        if (originalValue != null && clazz.isInstance(originalValue)) {
            return (T) originalValue;
        }
        return null;
    }
    
    /**
     * DLQ 메시지 요약 정보 반환
     */
    public String getSummary() {
        return String.format("DLQ[%s:%d:%d] Error: %s at %s", 
            originalTopic, 
            originalPartition, 
            originalOffset, 
            errorClass,
            getFailureDateTime());
    }
}
