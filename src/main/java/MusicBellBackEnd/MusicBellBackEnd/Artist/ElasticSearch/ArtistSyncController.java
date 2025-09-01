package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/artist/es/sync")

@Slf4j
public class ArtistSyncController {
    
    private final ArtistSyncService artistSyncService;

    @PostMapping("/all")
    public ResponseEntity<String> syncAllArtists() {
        try {
            log.info("🚀 전체 아티스트 동기화 요청");
            artistSyncService.syncAllArtists();
            return ResponseEntity.ok("전체 아티스트 동기화가 완료되었습니다.");
        } catch (Exception e) {
            log.error("❌ 전체 동기화 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("동기화 실패: " + e.getMessage());
        }
    }


    @PostMapping("/active")
    public ResponseEntity<String> syncActiveArtists() {
        try {
            log.info("🚀 활성 아티스트 동기화 요청");
            artistSyncService.syncActiveArtistsOnly();
            return ResponseEntity.ok("활성 아티스트 동기화가 완료되었습니다.");
        } catch (Exception e) {
            log.error("❌ 활성 아티스트 동기화 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("동기화 실패: " + e.getMessage());
        }
    }


    @PostMapping("/artist/{artistId}")
    public ResponseEntity<String> syncSingleArtist(
        @PathVariable Long artistId
    ) {
        try {
            log.info("🔄 단일 아티스트 동기화 요청: artistId={}", artistId);
            artistSyncService.syncSingleArtist(artistId);
            return ResponseEntity.ok("아티스트 동기화가 완료되었습니다.");
        } catch (Exception e) {
            log.error("❌ 단일 동기화 실패: artistId={}, error={}", artistId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("동기화 실패: " + e.getMessage());
        }
    }


    @PostMapping("/stats/{artistId}")
    public ResponseEntity<String> syncArtistStats(
        @PathVariable Long artistId
    ) {
        try {
            log.info("📊 아티스트 통계 동기화 요청: artistId={}", artistId);
            artistSyncService.syncSingleArtist(artistId);
            return ResponseEntity.ok("아티스트 통계 동기화가 완료되었습니다.");
        } catch (Exception e) {
            log.error("❌ 통계 동기화 실패: artistId={}, error={}", artistId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("통계 동기화 실패: " + e.getMessage());
        }
    }


    @DeleteMapping("/artist/{artistId}")
    public ResponseEntity<String> deleteFromIndex(
        @PathVariable Long artistId
    ) {
        try {
            log.info("🗑️ 인덱스에서 아티스트 삭제 요청: artistId={}", artistId);
            artistSyncService.deleteFromIndex(artistId);
            return ResponseEntity.ok("인덱스에서 아티스트가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("❌ 인덱스 삭제 실패: artistId={}, error={}", artistId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("삭제 실패: " + e.getMessage());
        }
    }


    @GetMapping("/status")
    public ResponseEntity<ArtistSyncService.SyncStatus> getSyncStatus() {
        try {
            log.info("📊 동기화 상태 확인 요청");
            ArtistSyncService.SyncStatus status = artistSyncService.getSyncStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ 상태 확인 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
