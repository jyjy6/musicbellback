package MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LyricsGenerationRequest {
    private Long musicId;
    private String musicUrl;
    private String musicTitle;
    private String artistName;
    private Integer duration;
    private String targetLanguage = "KO"; // 기본값: 한국어
    private Boolean generateSync = true; // 동기화 가사 생성 여부
}