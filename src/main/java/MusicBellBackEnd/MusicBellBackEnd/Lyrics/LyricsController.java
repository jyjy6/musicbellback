package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsGenerationRequest;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsManualSaveRequest;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/lyrics")
@RequiredArgsConstructor
@Slf4j
public class LyricsController {
    
    private final LyricsService lyricsService;
    
    /**
     * 음악 ID로 가사 조회
     */
    @GetMapping("/{musicId}")
    public ResponseEntity<LyricsResponse> getLyricsByMusicId(@PathVariable Long musicId) {
        log.info("가사 조회 요청: musicId={}", musicId);
        
        Optional<LyricsResponse> lyrics = lyricsService.getLyricsByMusicId(musicId);
        
        if (lyrics.isPresent()) {
            return ResponseEntity.ok(lyrics.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * AI를 사용하여 가사 자동 생성
     */
    @PostMapping("/generate")
    public ResponseEntity<LyricsResponse> generateLyrics(@RequestBody LyricsGenerationRequest request) {
        log.info("가사 자동 생성 요청: musicId={}", request.getMusicId());
        
        LyricsResponse response = lyricsService.generateLyrics(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 수동 가사 저장/업데이트 (라인 포함)
     */
    @PostMapping("/manual")
    public ResponseEntity<LyricsResponse> saveManualLyrics(@RequestBody LyricsManualSaveRequest request) {
        log.info("수동 가사 저장 요청: musicId={}", request.getMusicId());
        LyricsResponse response = lyricsService.saveManualLyrics(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 가사 삭제
     */
    @DeleteMapping("/{lyricsId}")
    public ResponseEntity<Void> deleteLyrics(@PathVariable Long lyricsId) {
        log.info("가사 삭제 요청: lyricsId={}", lyricsId);
        
        lyricsService.deleteLyrics(lyricsId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 가사 상태 확인 (음악에 가사가 있는지 확인)
     */
    @GetMapping("/exists/{musicId}")
    public ResponseEntity<Boolean> checkLyricsExists(@PathVariable Long musicId) {
        log.info("가사 존재 여부 확인: musicId={}", musicId);
        
        Optional<LyricsResponse> lyrics = lyricsService.getLyricsByMusicId(musicId);
        log.info(lyrics.toString());
        return ResponseEntity.ok(lyrics.isPresent());
    }
}