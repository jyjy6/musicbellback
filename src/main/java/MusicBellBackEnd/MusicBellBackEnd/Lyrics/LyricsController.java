package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsGenerationRequest;
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
@CrossOrigin(origins = "*")
public class LyricsController {
    
    private final LyricsService lyricsService;
    
    /**
     * 음악 ID로 가사 조회
     */
    @GetMapping("/music/{musicId}")
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
     * 가사 번역
     */
    @PostMapping("/{lyricsId}/translate")
    public ResponseEntity<LyricsResponse> translateLyrics(
            @PathVariable Long lyricsId,
            @RequestParam String targetLanguage) {
        log.info("가사 번역 요청: lyricsId={}, targetLanguage={}", lyricsId, targetLanguage);
        
        LyricsResponse response = lyricsService.translateLyrics(lyricsId, targetLanguage);
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
    @GetMapping("/music/{musicId}/exists")
    public ResponseEntity<Boolean> checkLyricsExists(@PathVariable Long musicId) {
        log.info("가사 존재 여부 확인: musicId={}", musicId);
        
        Optional<LyricsResponse> lyrics = lyricsService.getLyricsByMusicId(musicId);
        return ResponseEntity.ok(lyrics.isPresent());
    }
}