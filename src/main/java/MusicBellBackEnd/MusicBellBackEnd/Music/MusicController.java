package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Auth.CustomUserDetails;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.*;
import MusicBellBackEnd.MusicBellBackEnd.Redis.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequestMapping("/api/v1/music")
@RequiredArgsConstructor
@RestController
public class MusicController {

    private final MusicService musicService;
    private final PlaylistService playlistService;
    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;
    private final RankingService rankingService;
    private final RecentPlayService recentPlayService;
    // Presigned URL 생성 (음악 파일 및 이미지)
    @GetMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponseDto> getPresignedUrl(
            @RequestParam String filename,
            @RequestParam String filetype
    ) {
        try {
            String decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
            String randomFilename = UUID.randomUUID().toString() + "_" + decodedFilename;

            // 파일 타입에 따른 폴더 구분
            String folder = filetype.startsWith("audio/") ? "music" : "images";
            String fullPath = folder + "/" + randomFilename;
            
            String presignedUrl = musicService.createPresignedUrl(fullPath);
            String usableUrl = "https://" + bucket + ".s3.amazonaws.com/" + fullPath;

            PresignedUrlResponseDto response = PresignedUrlResponseDto.builder()
                    .presignedUrl(presignedUrl)
                    .usableUrl(usableUrl)
                    .build();

            log.info("Presigned URL 생성 완료: {}", filename);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: {}", e.getMessage());
            throw new GlobalException("Presigned URL 생성에 실패했습니다.", "PRESIGNED_URL_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 음악 업로드
    @PostMapping("/upload")
    public ResponseEntity<List<MusicResponseDto>> uploadMusic(
            @RequestBody List<MusicRequestDto> musicRequests, 
            Authentication auth
    ) {
//        if (auth == null) {
//            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
//        }

        List<MusicResponseDto> savedMusics = musicService.uploadMusics(musicRequests, auth);
        log.info("사용자 {}가 {}개의 음악을 업로드했습니다.", auth.getName(), savedMusics.size());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMusics);
    }

    // 음악 목록 조회 (페이징)
    @GetMapping
    public ResponseEntity<MusicPageResponseDto> getAllMusics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        MusicPageResponseDto musics = musicService.getAllMusics(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(musics);
    }

    // 음악 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<MusicResponseDto> getMusicById(@PathVariable Long id, Authentication auth) {
        MusicResponseDto music = musicService.getMusicById(id, auth);
        return ResponseEntity.ok(music);
    }

    // 음악 검색
    @GetMapping("/search")
    public ResponseEntity<MusicPageResponseDto> searchMusics(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String uploaderName,
            @RequestParam(required = false) String musicGrade,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MusicSearchDto searchDto = MusicSearchDto.builder()
                .title(title)
                .artist(artist)
                .album(album)
                .genre(genre)
                .uploaderName(uploaderName)
                .musicGrade(musicGrade)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .page(page)
                .size(size)
                .build();

        MusicPageResponseDto searchResults = musicService.searchMusics(searchDto);
        return ResponseEntity.ok(searchResults);
    }

    // 음악 정보 수정
    @PutMapping("/{id}")
    public ResponseEntity<MusicResponseDto> updateMusic(
            @PathVariable Long id,
            @RequestBody MusicUpdateDto updateDto,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        MusicResponseDto updatedMusic = musicService.updateMusic(id, updateDto, auth.getName());
        log.info("사용자 {}가 음악 ID {}를 수정했습니다.", auth.getName(), id);
        
        return ResponseEntity.ok(updatedMusic);
    }

    // 음악 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMusic(
            @PathVariable Long id,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        musicService.deleteMusic(id, auth.getName());
        log.info("사용자 {}가 음악 ID {}를 삭제했습니다.", auth.getName(), id);
        
        return ResponseEntity.ok(Map.of("message", "음악이 성공적으로 삭제되었습니다."));
    }


    // 좋아요 토글
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long id,
            @RequestParam boolean isLike,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        boolean result = musicService.toggleLike(id, isLike);
        String action = result ? "좋아요" : "좋아요 취소";
        log.info("사용자 {}가 음악 ID {}에 {}했습니다.", auth.getName(), id, action);
        
        return ResponseEntity.ok(Map.of(
                "message", action + "가 완료되었습니다.",
                "isLiked", result
        ));
    }

    // 인기 음악 TOP 10
    @GetMapping("/popular")
    public ResponseEntity<List<MusicStatsDto>> getPopularMusics() {
        List<MusicStatsDto> popularMusics = musicService.getPopularMusics();
        return ResponseEntity.ok(popularMusics);
    }

    // 최신 음악 TOP 10
    @GetMapping("/latest")
    public ResponseEntity<List<MusicStatsDto>> getLatestMusics() {
        List<MusicStatsDto> latestMusics = musicService.getLatestMusics();
        return ResponseEntity.ok(latestMusics);
    }

    // 특정 업로더의 음악 목록
    @GetMapping("/uploader/{uploaderName}")
    public ResponseEntity<MusicPageResponseDto> getMusicsByUploader(
            @PathVariable String uploaderName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MusicSearchDto searchDto = MusicSearchDto.builder()
                .uploaderName(uploaderName)
                .page(page)
                .size(size)
                .sortBy("createdAt")
                .sortOrder("desc")
                .build();

        MusicPageResponseDto uploaderMusics = musicService.searchMusics(searchDto);
        return ResponseEntity.ok(uploaderMusics);
    }

    // 장르별 음악 목록
    @GetMapping("/genre/{genre}")
    public ResponseEntity<MusicPageResponseDto> getMusicsByGenre(
            @PathVariable String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "playCount") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        MusicSearchDto searchDto = MusicSearchDto.builder()
                .genre(genre)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();

        MusicPageResponseDto genreMusics = musicService.searchMusics(searchDto);
        return ResponseEntity.ok(genreMusics);
    }

    // === 플레이리스트 관련 엔드포인트 ===

    // 사용자 플레이리스트 조회
    @GetMapping("/playlist")
    public ResponseEntity<List<PlaylistItem>> getPlaylist(Authentication auth) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        List<PlaylistItem> playlist = playlistService.getPlaylist(userId);
        return ResponseEntity.ok(playlist);
    }

    // 플레이리스트에 음악 수동 추가
    @PostMapping("/playlist/{musicId}")
    public ResponseEntity<Map<String, String>> addToPlaylist(
            @PathVariable Long musicId,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        
        // 음악 정보 조회
        MusicResponseDto music = musicService.getMusicById(musicId, null); // 플레이리스트 자동 추가 방지
        
        playlistService.addToPlaylist(userId, music.getId(), music.getTitle(), music.getMusicUrl());
        log.info("사용자 ID {}가 음악 ID {}를 플레이리스트에 추가했습니다.", userId, musicId);
        
        return ResponseEntity.ok(Map.of("message", "플레이리스트에 추가되었습니다."));
    }

    // 플레이리스트에서 음악 제거 (musicId로)
    @DeleteMapping("/playlist/{musicId}")
    public ResponseEntity<Map<String, String>> removeFromPlaylist(
            @PathVariable Long musicId,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        playlistService.removeFromPlaylistByMusicId(userId, musicId);
        log.info("사용자 ID {}가 음악 ID {}를 플레이리스트에서 제거했습니다.", userId, musicId);
        
        return ResponseEntity.ok(Map.of("message", "플레이리스트에서 제거되었습니다."));
    }

    // 플레이리스트에서 음악 제거 (인덱스로)
    @DeleteMapping("/playlist/index/{index}")
    public ResponseEntity<Map<String, String>> removeFromPlaylistByIndex(
            @PathVariable int index,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        playlistService.removeFromPlaylist(userId, index);
        log.info("사용자 ID {}가 플레이리스트 인덱스 {}를 제거했습니다.", userId, index);
        
        return ResponseEntity.ok(Map.of("message", "플레이리스트에서 제거되었습니다."));
    }

    // 플레이리스트 전체 삭제
    @DeleteMapping("/playlist")
    public ResponseEntity<Map<String, String>> clearPlaylist(Authentication auth) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        playlistService.clearPlaylist(userId);
        log.info("사용자 ID {}가 플레이리스트를 전체 삭제했습니다.", userId);
        
        return ResponseEntity.ok(Map.of("message", "플레이리스트가 전체 삭제되었습니다."));
    }

    // 플레이리스트 순서 변경
    @PutMapping("/playlist/move")
    public ResponseEntity<Map<String, String>> movePlaylistItem(
            @RequestParam int fromIndex,
            @RequestParam int toIndex,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        playlistService.movePlaylistItem(userId, fromIndex, toIndex);
        log.info("사용자 ID {}가 플레이리스트 순서를 변경했습니다. {} -> {}", userId, fromIndex, toIndex);
        
        return ResponseEntity.ok(Map.of("message", "플레이리스트 순서가 변경되었습니다."));
    }

    // 플레이리스트 개수 조회
    @GetMapping("/playlist/count")
    public ResponseEntity<Map<String, Integer>> getPlaylistCount(Authentication auth) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        int count = playlistService.getPlaylistCount(userId);
        
        return ResponseEntity.ok(Map.of("count", count));
    }

    // 특정 음악이 플레이리스트에 있는지 확인
    @GetMapping("/playlist/contains/{musicId}")
    public ResponseEntity<Map<String, Boolean>> isInPlaylist(
            @PathVariable Long musicId,
            Authentication auth
    ) {
        if (auth == null) {
            throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
        boolean isInPlaylist = playlistService.isInPlaylist(userId, musicId);
        
        return ResponseEntity.ok(Map.of("isInPlaylist", isInPlaylist));
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<Long>> getRanking(
            @RequestParam(defaultValue = "music") String table,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "10") int limit) {

        List<Long> topMusics = rankingService.getTop(table, period, limit);
        log.info(topMusics.toString());
        return ResponseEntity.ok(topMusics);
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentPlay(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails) {
            Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
            List<RecentPlayItem> recentPlays = recentPlayService.getRecentPlays(userId);
            return ResponseEntity.ok(recentPlays);
        }

        // 로그인 안 된 경우 아무 동작 안 함 (204 No Content or 401 Unauthorized 등 선택 가능)
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/recent")
    public ResponseEntity<?> clearRecentPlay(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails) {
            Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
            recentPlayService.clearRecentPlays(userId);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // 재생 카운트만 증가 (플레이리스트 재생용 경량 API)
    @PostMapping("/{id}/play")
    public ResponseEntity<Map<String, String>> incrementPlayCount(
            @PathVariable Long id,
            Authentication auth
    ) {
        try {
            // 재생 카운트 증가
            musicService.incrementPlayCount(id);
            
            // 로그인된 사용자의 경우 최근 재생 목록에 추가
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails) {
                try {
                    Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
                    // 음악 기본 정보만 가져오기 (재생 카운트 중복 증가 방지)
                    MusicResponseDto music = musicService.getMusicByIdWithoutIncrement(id);
                    recentPlayService.addRecentPlay(userId, music.getId(), music.getTitle(), 
                            music.getAlbumImageUrl(), music.getArtist(), music.getDuration());
                    log.info("사용자 ID {}의 최근 재생 목록에 음악 ID {} 추가됨", userId, id);
                } catch (Exception e) {
                    log.warn("최근 재생 목록 추가 중 오류 발생: {}", e.getMessage());
                    // 최근 재생 목록 추가 실패해도 재생 카운트 증가는 정상 진행
                }
            }
            
            log.info("음악 ID {} 재생 카운트가 증가되었습니다.", id);
            return ResponseEntity.ok(Map.of("message", "재생 카운트가 증가되었습니다."));
        } catch (Exception e) {
            log.error("재생 카운트 증가 실패: {}", e.getMessage());
            throw new GlobalException("재생 카운트 증가에 실패했습니다.", "PLAY_COUNT_INCREMENT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

