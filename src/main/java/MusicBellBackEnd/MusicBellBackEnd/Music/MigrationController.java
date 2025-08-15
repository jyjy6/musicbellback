package MusicBellBackEnd.MusicBellBackEnd.Music;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/migration")
@RequiredArgsConstructor
public class MigrationController {
    
    private final MigrationService migrationService;
    
    /**
     * 마이그레이션 상태 확인
     */
    @GetMapping("/status")
    public ResponseEntity<MigrationService.MigrationStatus> getMigrationStatus() {
        MigrationService.MigrationStatus status = migrationService.checkMigrationStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * 아티스트 데이터 마이그레이션 실행
     * 주의: 시간이 오래 걸릴 수 있으므로 개발 환경에서만 사용
     */
    @PostMapping("/migrate-artists")
    public ResponseEntity<Map<String, String>> migrateArtistData() {
        try {
            log.info("관리자에 의한 아티스트 마이그레이션 시작");
            
            // 비동기로 실행하는 것이 좋지만, 일단 동기로 구현
            migrationService.migrateArtistData();
            
            return ResponseEntity.ok(Map.of(
                "message", "아티스트 데이터 마이그레이션이 완료되었습니다.",
                "status", "success"
            ));
            
        } catch (Exception e) {
            log.error("마이그레이션 실행 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "message", "마이그레이션 실행 중 오류가 발생했습니다: " + e.getMessage(),
                "status", "error"
            ));
        }
    }
}
