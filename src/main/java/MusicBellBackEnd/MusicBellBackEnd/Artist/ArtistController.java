package MusicBellBackEnd.MusicBellBackEnd.Artist;

import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.*;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RequestMapping("/api/v1/artist")
@RequiredArgsConstructor
@RestController
public class ArtistController {

    private final ArtistService artistService;

    // 아티스트 등록
    @PostMapping
    public ResponseEntity<ArtistResponseDto> createArtist(
            @RequestBody ArtistRequestDto requestDto,
            Authentication auth
    ) {
        // TODO: 관리자 권한 체크 (나중에 추가)
        // if (auth == null) {
        //     throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        // }

        try {
            ArtistResponseDto createdArtist = artistService.createArtist(requestDto);
            log.info("새 아티스트 등록: {} (ID: {})", createdArtist.getName(), createdArtist.getId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(createdArtist);
        } catch (IllegalArgumentException e) {
            throw new GlobalException(e.getMessage(), "INVALID_ARTIST_DATA", HttpStatus.BAD_REQUEST);
        }
    }

    // 아티스트 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ArtistResponseDto> getArtistById(@PathVariable Long id) {
        ArtistResponseDto artist = artistService.getArtistById(id);
        return ResponseEntity.ok(artist);
    }

    // 아티스트 목록 조회 (페이징)
    @GetMapping
    public ResponseEntity<ArtistPageResponseDto> getAllArtists(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        ArtistPageResponseDto artists = artistService.getAllArtists(page, size, sortBy, sortOrder);
        return ResponseEntity.ok(artists);
    }

    // 아티스트 검색
    @GetMapping("/search")
    public ResponseEntity<ArtistPageResponseDto> searchArtists(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ArtistSearchDto searchDto = ArtistSearchDto.builder()
                .name(name)
                .genre(genre)
                .country(country)
                .isVerified(isVerified)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .page(page)
                .size(size)
                .build();
        ArtistPageResponseDto searchResults = artistService.searchArtists(searchDto);
        return ResponseEntity.ok(searchResults);
    }

    // 아티스트명으로 검색 (자동완성용)
    @GetMapping("/autocomplete")
    public ResponseEntity<List<ArtistResponseDto>> getArtistAutocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (query.trim().length() < 2) {
            throw new GlobalException("검색어는 최소 2글자 이상이어야 합니다.", "INVALID_QUERY", HttpStatus.BAD_REQUEST);
        }

        List<ArtistResponseDto> suggestions = artistService.getArtistSuggestions(query.trim(), limit);
        return ResponseEntity.ok(suggestions);
    }

    // 인기 아티스트 TOP 10
    @GetMapping("/popular")
    public ResponseEntity<List<ArtistResponseDto>> getPopularArtists() {
        List<ArtistResponseDto> popularArtists = artistService.getPopularArtists();
        return ResponseEntity.ok(popularArtists);
    }

    // 최신 등록 아티스트 TOP 10
    @GetMapping("/latest")
    public ResponseEntity<List<ArtistResponseDto>> getLatestArtists() {
        List<ArtistResponseDto> latestArtists = artistService.getLatestArtists();
        return ResponseEntity.ok(latestArtists);
    }

    // 아티스트 정보 수정
    @PutMapping("/{id}")
    public ResponseEntity<ArtistResponseDto> updateArtist(
            @PathVariable Long id,
            @RequestBody ArtistRequestDto requestDto,
            Authentication auth
    ) {
        // TODO: 관리자 권한 체크
        // if (auth == null) {
        //     throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        // }

        ArtistResponseDto updatedArtist = artistService.updateArtist(id, requestDto);
        log.info("아티스트 정보 수정: {} (ID: {})", updatedArtist.getName(), id);
        
        return ResponseEntity.ok(updatedArtist);
    }

    // 아티스트 활성화/비활성화
    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> toggleArtistStatus(
            @PathVariable Long id,
            @RequestParam boolean isActive,
            Authentication auth
    ) {
        // TODO: 관리자 권한 체크
        // if (auth == null) {
        //     throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        // }

        boolean result = artistService.toggleArtistStatus(id, isActive);
        String action = result ? "활성화" : "비활성화";
        log.info("아티스트 ID {} {}됨", id, action);
        
        return ResponseEntity.ok(Map.of(
                "message", "아티스트가 " + action + "되었습니다.",
                "isActive", result
        ));
    }

    // 아티스트 인증 상태 변경
    @PatchMapping("/{id}/verify")
    public ResponseEntity<Map<String, Object>> toggleArtistVerification(
            @PathVariable Long id,
            @RequestParam boolean isVerified,
            Authentication auth
    ) {
        // TODO: 관리자 권한 체크
        // if (auth == null) {
        //     throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        // }

        boolean result = artistService.toggleArtistVerification(id, isVerified);
        String action = result ? "인증" : "인증 해제";
        log.info("아티스트 ID {} {}됨", id, action);
        
        return ResponseEntity.ok(Map.of(
                "message", "아티스트가 " + action + "되었습니다.",
                "isVerified", result
        ));
    }

    // 아티스트 삭제 (소프트 삭제 - isActive = false)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteArtist(
            @PathVariable Long id,
            Authentication auth
    ) {
        // TODO: 관리자 권한 체크
        // if (auth == null) {
        //     throw new GlobalException("로그인이 필요합니다.", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        // }

        artistService.deleteArtist(id);
        log.info("아티스트 ID {} 삭제됨", id);
        
        return ResponseEntity.ok(Map.of("message", "아티스트가 성공적으로 삭제되었습니다."));
    }

    // 아티스트 중복 체크
    @GetMapping("/check-name")
    public ResponseEntity<Map<String, Object>> checkArtistName(@RequestParam String name) {
        if (name.trim().length() < 2) {
            throw new GlobalException("아티스트명은 최소 2글자 이상이어야 합니다.", "INVALID_NAME", HttpStatus.BAD_REQUEST);
        }

        boolean exists = artistService.existsByName(name.trim());
        
        return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "이미 등록된 아티스트명입니다." : "사용 가능한 아티스트명입니다."
        ));
    }
}
