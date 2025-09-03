package MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LyricsResponse {
    private Long id;
    private Long musicId;
    private String fullLyrics;
    private String syncedLyrics;
    private String language;
    private String source;
    private String generationType;
    private Boolean isVerified;
    private Boolean isActive;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LyricsLineResponse> lines;
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LyricsLineResponse {
        private Long id;
        private Integer startTime;
        private Integer endTime;
        private String text;
        private Integer lineOrder;
        private String type;
    }
}