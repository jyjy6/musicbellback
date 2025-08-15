package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistEntity;
import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {
    
    private final MusicRepository musicRepository;
    private final ArtistService artistService;
    
    /**
     * 기존 String artistName 데이터를 ArtistEntity 관계로 마이그레이션
     * 배치 처리로 안전하게 실행
     */
    @Transactional
    public void migrateArtistData() {
        log.info("=== 아티스트 데이터 마이그레이션 시작 ===");
        
        int batchSize = 100;
        int pageNumber = 0;
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            
            // 기존 artist는 있지만 artistEntity가 null인 데이터 조회
            Page<MusicEntity> musicsToMigrate = musicRepository.findMusicNeedingMigration(pageable);
            
            if (musicsToMigrate.isEmpty()) {
                log.info("마이그레이션할 데이터가 더 이상 없습니다.");
                break;
            }
            
            log.info("배치 {} 처리 중... ({}/{})", pageNumber + 1, 
                    musicsToMigrate.getNumberOfElements(), musicsToMigrate.getTotalElements());
            
            for (MusicEntity music : musicsToMigrate.getContent()) {
                try {
                    processedCount.incrementAndGet();
                    
                    if (music.getArtist() == null || music.getArtist().trim().isEmpty()) {
                        log.warn("음악 ID {}의 기존 artist가 비어있음", music.getId());
                        continue;
                    }
                    
                    // 아티스트 찾기 또는 생성
                    ArtistEntity artist = artistService.findOrCreateArtist(music.getArtist());
                    
                    // 관계 설정
                    music.migrateToArtistEntity(artist);
                    musicRepository.save(music);
                    
                    successCount.incrementAndGet();
                    
                    if (processedCount.get() % 50 == 0) {
                        log.info("진행률: {}/{} 완료", successCount.get(), processedCount.get());
                    }
                    
                } catch (Exception e) {
                    log.error("음악 ID {} 마이그레이션 실패: {}", music.getId(), e.getMessage(), e);
                }
            }
            
            pageNumber++;
        }
        
        log.info("=== 아티스트 데이터 마이그레이션 완료 ===");
        log.info("총 처리: {} 건, 성공: {} 건, 실패: {} 건", 
                processedCount.get(), successCount.get(), processedCount.get() - successCount.get());
    }
    
    /**
     * 마이그레이션 상태 확인
     */
    public MigrationStatus checkMigrationStatus() {
        long totalMusic = musicRepository.count();
        long migratedMusic = musicRepository.countMigratedMusic();
        long unmigrated = totalMusic - migratedMusic;
        
        return MigrationStatus.builder()
                .totalMusic(totalMusic)
                .migratedMusic(migratedMusic)
                .unmigratedMusic(unmigrated)
                .migrationProgress(totalMusic > 0 ? (double) migratedMusic / totalMusic * 100 : 0)
                .build();
    }
    
    /**
     * 마이그레이션 결과를 담는 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class MigrationStatus {
        private long totalMusic;
        private long migratedMusic;
        private long unmigratedMusic;
        private double migrationProgress; // 퍼센트
    }
}
