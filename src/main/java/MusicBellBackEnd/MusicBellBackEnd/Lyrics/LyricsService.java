package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsGenerationRequest;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.LyricsResponse;
import MusicBellBackEnd.MusicBellBackEnd.Music.MusicEntity;
import MusicBellBackEnd.MusicBellBackEnd.Music.MusicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LyricsService {
    
    private final LyricsRepository lyricsRepository;
    private final LyricsLineRepository lyricsLineRepository;
    private final MusicRepository musicRepository;
    private final GeminiLyricsService geminiLyricsService;
    
    /**
     * 음악 ID로 가사 조회
     */
    public Optional<LyricsResponse> getLyricsByMusicId(Long musicId) {
        return lyricsRepository.findActiveLyricsByMusicId(musicId)
                .map(this::convertToResponse);
    }
    
    /**
     * AI를 사용하여 가사 자동 생성
     */
    @Transactional
    public LyricsResponse generateLyrics(LyricsGenerationRequest request) {
        log.info("가사 자동 생성 시작: musicId={}", request.getMusicId());
        
        // 음악 정보 조회
        MusicEntity music = musicRepository.findById(request.getMusicId())
                .orElseThrow(() -> new GlobalException(
                        "음악을 찾을 수 없습니다",
                        "MUSIC_NOT_FOUND",
                        HttpStatus.NOT_FOUND));
        
        // 이미 가사가 있는지 확인
        if (lyricsRepository.existsByMusicId(request.getMusicId())) {
            throw new GlobalException(
                    "이미 가사가 존재합니다",
                    "LYRICS_ALREADY_EXISTS",
                    HttpStatus.CONFLICT);
        }
        
        try {
            // Whisper를 사용하여 가사 생성
            String generatedLyrics = geminiLyricsService.generateLyrics(request);
            
            // 가사 엔티티 생성
            LyricsEntity lyrics = LyricsEntity.builder()
                    .music(music)
                    .fullLyrics(generatedLyrics)
                    .language(request.getTargetLanguage())
                    .source("AI_GENERATED")
                    .generationType("SPEECH_TO_TEXT")
                    .isVerified(false)
                    .isActive(true)
                    .createdBy("WHISPER_AI")
                    .build();
            
            // 동기화 가사 생성 (선택적)
            if (request.getGenerateSync()) {
                String syncedLyrics = generateSyncedLyrics(generatedLyrics, music.getDuration());
                lyrics.setSyncedLyrics(syncedLyrics);
            }
            
            // 먼저 LyricsEntity 저장 (ID 생성)
            LyricsEntity savedLyrics = lyricsRepository.save(lyrics);
            log.info("가사 엔티티 저장 완료: lyricsId={}", savedLyrics.getId());
            
            // 동기화 가사가 있으면 가사 라인 생성
            if (request.getGenerateSync() && savedLyrics.getSyncedLyrics() != null) {
                createLyricsLines(savedLyrics, savedLyrics.getSyncedLyrics());
            }
            
            log.info("가사 생성 완료: lyricsId={}", savedLyrics.getId());
            return convertToResponse(savedLyrics);
            
        } catch (Exception e) {
            log.error("가사 생성 실패: musicId={}, error={}", request.getMusicId(), e.getMessage());
            throw new GlobalException(
                    "가사 생성에 실패했습니다: " + e.getMessage(),
                    "LYRICS_GENERATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 가사 번역
     */
    @Transactional
    public LyricsResponse translateLyrics(Long lyricsId, String targetLanguage) {
        LyricsEntity lyrics = lyricsRepository.findById(lyricsId)
                .orElseThrow(() -> new GlobalException(
                        "가사를 찾을 수 없습니다",
                        "LYRICS_NOT_FOUND",
                        HttpStatus.NOT_FOUND));
        
        try {
            String translatedLyrics = geminiLyricsService.translateLyrics(
                    lyrics.getFullLyrics(), 
                    lyrics.getLanguage(), 
                    targetLanguage
            );
            
            // 새로운 번역된 가사 엔티티 생성
            LyricsEntity translatedLyricsEntity = LyricsEntity.builder()
                    .music(lyrics.getMusic())
                    .fullLyrics(translatedLyrics)
                    .language(targetLanguage)
                    .source("AI_TRANSLATED")
                    .generationType("TRANSLATION")
                    .isVerified(false)
                    .isActive(true)
                    .createdBy("GEMINI_AI")
                    .build();
            
            LyricsEntity savedLyrics = lyricsRepository.save(translatedLyricsEntity);
            return convertToResponse(savedLyrics);
            
        } catch (Exception e) {
            log.error("가사 번역 실패: lyricsId={}, error={}", lyricsId, e.getMessage());
            throw new GlobalException(
                    "가사 번역에 실패했습니다: " + e.getMessage(),
                    "LYRICS_TRANSLATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * 가사 삭제
     */
    @Transactional
    public void deleteLyrics(Long lyricsId) {
        LyricsEntity lyrics = lyricsRepository.findById(lyricsId)
                .orElseThrow(() -> new GlobalException(
                        "가사를 찾을 수 없습니다",
                        "LYRICS_NOT_FOUND",
                        HttpStatus.NOT_FOUND));
        
        // 가사 라인들 먼저 삭제
        lyricsLineRepository.deleteByLyricsId(lyricsId);
        
        // 가사 삭제
        lyricsRepository.delete(lyrics);
        
        log.info("가사 삭제 완료: lyricsId={}", lyricsId);
    }
    
    /**
     * 동기화된 가사 생성 (간단한 구현)
     */
    private String generateSyncedLyrics(String lyrics, Integer duration) {
        // 실제로는 더 정교한 알고리즘이 필요
        // 여기서는 간단하게 가사를 시간별로 분할
        String[] lines = lyrics.split("\n");
        StringBuilder syncedLyrics = new StringBuilder();
        
        if (duration != null && lines.length > 0) {
            int timePerLine = (duration * 1000) / lines.length; // 밀리초 단위
            
            for (int i = 0; i < lines.length; i++) {
                int startTime = i * timePerLine;
                int endTime = (i + 1) * timePerLine;
                
                syncedLyrics.append(String.format("[%02d:%02d.%02d]%s\n", 
                        startTime / 60000, 
                        (startTime % 60000) / 1000, 
                        (startTime % 1000) / 10,
                        lines[i]));
            }
        }
        
        return syncedLyrics.toString();
    }
    
    /**
     * 가사 라인 생성
     */
    private void createLyricsLines(LyricsEntity lyrics, String syncedLyrics) {
        if (lyrics.getId() == null) {
            log.error("LyricsEntity가 저장되지 않았습니다. ID가 null입니다.");
            return;
        }
        
        String[] lines = syncedLyrics.split("\n");
        log.info("가사 라인 생성 시작: lyricsId={}, 총 {} 라인", lyrics.getId(), lines.length);
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;
            
            try {
                // LRC 형식 파싱 (간단한 구현)
                if (line.startsWith("[") && line.contains("]")) {
                    String timeStr = line.substring(1, line.indexOf("]"));
                    String text = line.substring(line.indexOf("]") + 1);
                    
                    int startTime = parseTimeToMillis(timeStr);
                    int endTime = (i < lines.length - 1) ? 
                            parseTimeToMillis(lines[i + 1].substring(1, lines[i + 1].indexOf("]"))) :
                            startTime + 5000; // 기본 5초
                    
                    LyricsLineEntity lyricsLine = LyricsLineEntity.builder()
                            .lyrics(lyrics)
                            .startTime(startTime)
                            .endTime(endTime)
                            .text(text)
                            .lineOrder(i)
                            .type("VOCAL")
                            .build();
                    
                    lyricsLineRepository.save(lyricsLine);
                    log.debug("가사 라인 저장 완료: lineOrder={}, text={}", i, text);
                    
                } else {
                    // LRC 형식이 아닌 경우 간단한 시간 분할
                    int startTime = i * 5000; // 5초 간격
                    int endTime = (i + 1) * 5000;
                    
                    LyricsLineEntity lyricsLine = LyricsLineEntity.builder()
                            .lyrics(lyrics)
                            .startTime(startTime)
                            .endTime(endTime)
                            .text(line.trim())
                            .lineOrder(i)
                            .type("VOCAL")
                            .build();
                    
                    lyricsLineRepository.save(lyricsLine);
                    log.debug("가사 라인 저장 완료 (일반): lineOrder={}, text={}", i, line.trim());
                }
                
            } catch (Exception e) {
                log.warn("가사 라인 파싱 실패: line={}, error={}", line, e.getMessage());
                // 파싱 실패해도 계속 진행
            }
        }
        
        log.info("가사 라인 생성 완료: lyricsId={}", lyrics.getId());
    }
    
    /**
     * 시간 문자열을 밀리초로 변환
     */
    private int parseTimeToMillis(String timeStr) {
        // MM:SS.mm 형식 파싱
        String[] parts = timeStr.split(":");
        if (parts.length == 2) {
            int minutes = Integer.parseInt(parts[0]);
            String[] secParts = parts[1].split("\\.");
            int seconds = Integer.parseInt(secParts[0]);
            int millis = secParts.length > 1 ? Integer.parseInt(secParts[1]) * 10 : 0;
            
            return (minutes * 60 + seconds) * 1000 + millis;
        }
        return 0;
    }
    
    /**
     * Entity를 Response DTO로 변환
     */
    private LyricsResponse convertToResponse(LyricsEntity lyrics) {
        List<LyricsResponse.LyricsLineResponse> lines = 
                lyricsLineRepository.findByLyricsIdOrderByLineOrder(lyrics.getId())
                        .stream()
                        .map(line -> LyricsResponse.LyricsLineResponse.builder()
                                .id(line.getId())
                                .startTime(line.getStartTime())
                                .endTime(line.getEndTime())
                                .text(line.getText())
                                .lineOrder(line.getLineOrder())
                                .type(line.getType())
                                .build())
                        .collect(Collectors.toList());
        
        return LyricsResponse.builder()
                .id(lyrics.getId())
                .musicId(lyrics.getMusic().getId())
                .fullLyrics(lyrics.getFullLyrics())
                .syncedLyrics(lyrics.getSyncedLyrics())
                .language(lyrics.getLanguage())
                .source(lyrics.getSource())
                .generationType(lyrics.getGenerationType())
                .isVerified(lyrics.getIsVerified())
                .isActive(lyrics.getIsActive())
                .createdBy(lyrics.getCreatedBy())
                .createdAt(lyrics.getCreatedAt())
                .updatedAt(lyrics.getUpdatedAt())
                .lines(lines)
                .build();
    }
}