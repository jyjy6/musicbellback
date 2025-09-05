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
public class LyricsManualSaveRequest {
    private Long musicId;
    private String language; // optional, default KO
    private String createdBy; // user name or id
    private String fullLyrics; // optional, derive from lines if null
    private List<LyricsLineRequest> lines;
}


