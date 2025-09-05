package MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TranscriptionResult {
    private String fullText;
    private List<Segment> segments;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Segment {
        private Integer startMs;
        private Integer endMs;
        private String text;
        private Integer order;
        private String type; // default VOCAL
    }
}


