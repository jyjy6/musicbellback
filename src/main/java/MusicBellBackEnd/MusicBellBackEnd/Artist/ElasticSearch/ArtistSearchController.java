package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/artist/es/search")

@Slf4j
public class ArtistSearchController {
    
    private final ArtistSearchService artistSearchService;


    @GetMapping
    public ResponseEntity<Page<ArtistDocument>> smartSearch(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String genre,
        @RequestParam(required = false) String country,
        @RequestParam(required = false) Boolean isVerified,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.smartSearch(keyword, genre, country, isVerified, page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 아티스트 검색 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/genre/{genre}")
    public ResponseEntity<Page<ArtistDocument>> searchByGenre(
        @PathVariable String genre,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.searchByGenre(genre, page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 장르별 검색 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/country/{country}")
    public ResponseEntity<Page<ArtistDocument>> searchByCountry(
        @PathVariable String country,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.searchByCountry(country, page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 국가별 검색 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/popular")
    public ResponseEntity<Page<ArtistDocument>> getPopularArtists(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.getPopularArtists(page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 인기 아티스트 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/verified")
    public ResponseEntity<Page<ArtistDocument>> getVerifiedArtists(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.getVerifiedArtists(page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 인증 아티스트 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/recent")
    public ResponseEntity<Page<ArtistDocument>> getRecentArtists(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Page<ArtistDocument> results = artistSearchService.getRecentArtists(page, size);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ 신규 아티스트 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autoComplete(
        @RequestParam String prefix,
        @RequestParam(defaultValue = "10") int size
    ) {
        try {
            List<String> suggestions = artistSearchService.autoComplete(prefix, size);
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("❌ 자동완성 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/stats/genres")
    public ResponseEntity<List<ArtistSearchService.GenreStats>> getGenreStats() {
        try {
            List<ArtistSearchService.GenreStats> stats = artistSearchService.getGenreStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ 장르별 통계 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
