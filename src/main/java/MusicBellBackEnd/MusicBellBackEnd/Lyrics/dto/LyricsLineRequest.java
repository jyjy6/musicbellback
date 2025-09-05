package MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LyricsLineRequest {
    private Long id; // optional for future updates
    private Integer startTime; // milliseconds (backward compatibility)
    private Integer endTime;   // milliseconds (backward compatibility)
    private Integer startSec;  // seconds (preferred)
    private Integer endSec;    // seconds (preferred)
    private String text;
    private Integer lineOrder;
    private String type; // VOCAL, CHORUS, INSTRUMENTAL, etc.
}


