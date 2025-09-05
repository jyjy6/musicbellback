package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Lyrics.dto.*;
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
    private final OpenAIWhisperLyricsService openAIWhisperLyricsService;
    
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
            // Whisper를 사용하여 세그먼트 포함 가사 생성
            TranscriptionResult tr = openAIWhisperLyricsService.generateLyricsWithSegments(request);

            // 결과 검증: 비어있으면 실패 처리
            boolean hasSegments = tr != null && tr.getSegments() != null && !tr.getSegments().isEmpty();
            boolean hasText = tr != null && tr.getFullText() != null && !tr.getFullText().trim().isEmpty();
            if (!hasSegments && !hasText) {
                throw new GlobalException(
                        "Whisper 결과가 비어있습니다",
                        "WHISPER_EMPTY_RESULT",
                        HttpStatus.BAD_GATEWAY);
            }

            // 가사 엔티티 생성
            LyricsEntity lyrics = LyricsEntity.builder()
                    .music(music)
                    .fullLyrics(tr.getFullText())
                    .language(request.getTargetLanguage())
                    .source("AI_GENERATED")
                    .generationType("SPEECH_TO_TEXT")
                    .isVerified(false)
                    .isActive(true)
                    .createdBy("WHISPER_AI")
                    .build();

            // 옵션: LRC 같은 syncedLyrics를 원하면 여기서 생성 가능 (현재는 세그먼트 기반 라인 생성으로 대체)
            // 먼저 LyricsEntity 저장 (ID 생성)
            LyricsEntity savedLyrics = lyricsRepository.save(lyrics);
            log.info("가사 엔티티 저장 완료: lyricsId={}", savedLyrics.getId());

            // 세그먼트로 라인 생성
            if (tr.getSegments() != null && !tr.getSegments().isEmpty()) {
                int order = 0;
                for (TranscriptionResult.Segment seg : tr.getSegments()) {
                    LyricsLineEntity line = LyricsLineEntity.builder()
                            .lyrics(savedLyrics)
                            .startTime(seg.getStartMs())
                            .endTime(seg.getEndMs())
                            .text(seg.getText())
                            .lineOrder(seg.getOrder() != null ? seg.getOrder() : order)
                            .type(seg.getType() != null ? seg.getType() : "VOCAL")
                            .build();
                    lyricsLineRepository.save(line);
                    order++;
                }
            } else if (tr.getFullText() != null && !tr.getFullText().isEmpty()) {
                // 세그먼트가 없으면 간단 분할 (줄바꿈)
                String[] lines = tr.getFullText().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].trim().isEmpty()) continue;
                    LyricsLineEntity line = LyricsLineEntity.builder()
                            .lyrics(savedLyrics)
                            .startTime(i * 5000)
                            .endTime((i + 1) * 5000)
                            .text(lines[i].trim())
                            .lineOrder(i)
                            .type("VOCAL")
                            .build();
                    lyricsLineRepository.save(line);
                }
            }
            
            log.info("가사 생성 완료: lyricsId={}", savedLyrics.getId());
            return convertToResponse(savedLyrics);
            
        } catch (Exception e) {
            log.error("가사 생성 실패: musicId={}, error={}", request.getMusicId(), e.getMessage());
            if (e instanceof GlobalException) {
                throw e;
            }
            throw new GlobalException(
                    "가사 생성에 실패했습니다",
                    "LYRICS_GENERATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 수동 가사 저장/업데이트 (라인 직접 저장)
     */
    @Transactional
    public LyricsResponse saveManualLyrics(LyricsManualSaveRequest request) {
        log.info("수동 가사 저장 요청: musicId={}, lines={}개", request.getMusicId(),
                request.getLines() != null ? request.getLines().size() : 0);

        MusicEntity music = musicRepository.findById(request.getMusicId())
                .orElseThrow(() -> new GlobalException(
                        "음악을 찾을 수 없습니다",
                        "MUSIC_NOT_FOUND",
                        HttpStatus.NOT_FOUND));

        LyricsEntity lyrics = lyricsRepository.findByMusicId(request.getMusicId())
                .orElse(LyricsEntity.builder()
                        .music(music)
                        .language(request.getLanguage() != null ? request.getLanguage() : "KO")
                        .source("MANUAL")
                        .generationType("MANUAL_INPUT")
                        .isVerified(false)
                        .isActive(true)
                        .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "USER")
                        .build());

        // fullLyrics 세팅 (없으면 lines에서 합성)
        if (request.getFullLyrics() != null) {
            lyrics.setFullLyrics(request.getFullLyrics());
        } else if (request.getLines() != null && !request.getLines().isEmpty()) {
            String joined = request.getLines().stream()
                    .map(l -> l.getText() != null ? l.getText() : "")
                    .collect(java.util.stream.Collectors.joining("\n"));
            lyrics.setFullLyrics(joined);
        }
        lyrics.setLanguage(request.getLanguage() != null ? request.getLanguage() : lyrics.getLanguage());
        lyrics.setSource("MANUAL");
        lyrics.setGenerationType("MANUAL_INPUT");

        // 저장/업데이트
        LyricsEntity saved = lyricsRepository.save(lyrics);

        // 기존 라인 삭제 후 재저장
        lyricsLineRepository.deleteByLyricsId(saved.getId());
        if (request.getLines() != null) {
            for (LyricsLineRequest l : request.getLines()) {
                Integer startMs = l.getStartSec() != null ? l.getStartSec() * 1000 : l.getStartTime();
                Integer endMs = l.getEndSec() != null ? l.getEndSec() * 1000 : l.getEndTime();
                LyricsLineEntity line = LyricsLineEntity.builder()
                        .lyrics(saved)
                        .startTime(startMs != null ? startMs : 0)
                        .endTime(endMs != null ? endMs : (startMs != null ? startMs + 5000 : 5000))
                        .text(l.getText())
                        .lineOrder(l.getLineOrder())
                        .type(l.getType() != null ? l.getType() : "VOCAL")
                        .build();
                lyricsLineRepository.save(line);
            }
        }

        return convertToResponse(saved);
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