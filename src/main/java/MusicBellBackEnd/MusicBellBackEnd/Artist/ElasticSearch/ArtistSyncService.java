package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistEntity;
import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistRepository;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistSyncService {

    private final ArtistRepository artistRepository;
    private final ArtistSearchRepository artistSearchRepository;

    /**
     * 🔄 Artist 엔티티를 ArtistDocument로 변환
     */
    public ArtistDocument convertToDocument(ArtistEntity artist) {
        ArtistDocument document = new ArtistDocument();
        
        // 기본 정보
        document.setId(artist.getId().toString());
        document.setName(artist.getName());
        document.setDescription(artist.getDescription());
        document.setProfileImageUrl(artist.getProfileImageUrl());
        
        // 분류/장르 정보
        document.setGenre(artist.getGenre());
        document.setCountry(artist.getCountry());
        document.setAgency(artist.getAgency());
        
        // 상태 정보
        document.setIsVerified(artist.getIsVerified());
        document.setIsActive(artist.getIsActive());
        
        // 통계 정보
        document.setFollowerCount(artist.getFollowerCount());
        document.setTotalPlayCount(artist.getTotalPlayCount());
        document.setTotalLikeCount(artist.getTotalLikeCount());
        
        // 외부 연동 정보
        document.setSpotifyId(artist.getSpotifyId());
        document.setAppleMusicId(artist.getAppleMusicId());
        document.setYoutubeChannelId(artist.getYoutubeChannelId());
        document.setOfficialWebsite(artist.getOfficialWebsite());
        document.setInstagramHandle(artist.getInstagramHandle());
        document.setTwitterHandle(artist.getTwitterHandle());
        
        // 타임스탬프
        document.setCreatedAt(artist.getCreatedAt());
        document.setUpdatedAt(artist.getUpdatedAt());
        
        // 검색 텍스트와 인기도 점수 생성
        document.generateSearchText();
        document.calculatePopularityScore();
        
        return document;
    }

    /**
     * 🚀 단일 아티스트 동기화
     */
    @Transactional
    public void syncSingleArtist(Long artistId) {
        try {
            ArtistEntity artist = artistRepository.findById(artistId)
                    .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));
            
            ArtistDocument document = convertToDocument(artist);
            artistSearchRepository.save(document);
            
            log.info("✅ 아티스트 동기화 완료: id={}, name={}", artistId, artist.getName());
        } catch (Exception e) {
            log.error("❌ 아티스트 동기화 실패: id={}, error={}", artistId, e.getMessage());
            throw new GlobalException("아티스트 동기화 실패", "ARTIST_SYNC_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🔄 전체 아티스트 일괄 동기화
     */
    @Transactional
    public void syncAllArtists() {
        log.info("🚀 전체 아티스트 동기화 시작");
        
        int pageSize = 100;
        int pageNumber = 0;
        int totalSynced = 0;
        
        try {
            // 기존 인덱스 클리어
            artistSearchRepository.deleteAll();
            log.info("🗑️ 기존 ElasticSearch 인덱스 클리어 완료");
            
            Page<ArtistEntity> artistPage;
            do {
                artistPage = artistRepository.findAll(PageRequest.of(pageNumber, pageSize));
                
                List<ArtistDocument> documents = artistPage.getContent().stream()
                        .map(this::convertToDocument)
                        .collect(Collectors.toList());
                
                if (!documents.isEmpty()) {
                    artistSearchRepository.saveAll(documents);
                    totalSynced += documents.size();
                    log.info("📦 배치 동기화 완료: {} ~ {} (총 {}개)", 
                            pageNumber * pageSize + 1, 
                            pageNumber * pageSize + documents.size(),
                            totalSynced);
                }
                
                pageNumber++;
            } while (artistPage.hasNext());
            
            log.info("🎉 전체 아티스트 동기화 완료! 총 {}개 아티스트 처리", totalSynced);
            
        } catch (Exception e) {
            log.error("❌ 전체 아티스트 동기화 실패: {}", e.getMessage());
            throw new GlobalException("ElasticSearch 동기화 실패", "ELASTICSEARCH_SYNC_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 🎵 아티스트 통계 업데이트 시 재동기화
     */
    @Transactional
    public void syncArtistStats(Long artistId) {
        try {
            ArtistEntity artist = artistRepository.findById(artistId)
                    .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));
            
            ArtistDocument document = convertToDocument(artist);
            artistSearchRepository.save(document);
            
            log.info("📊 아티스트 통계 동기화 완료: id={}, name={}", artistId, artist.getName());
        } catch (Exception e) {
            log.error("❌ 아티스트 통계 동기화 실패: id={}, error={}", artistId, e.getMessage());
        }
    }

    /**
     * 🗑️ 아티스트 삭제 시 ElasticSearch에서도 제거
     */
    @Transactional
    public void deleteFromIndex(Long artistId) {
        try {
            artistSearchRepository.deleteById(artistId.toString());
            log.info("🗑️ ElasticSearch에서 아티스트 삭제 완료: id={}", artistId);
        } catch (Exception e) {
            log.error("❌ ElasticSearch 아티스트 삭제 실패: id={}, error={}", artistId, e.getMessage());
        }
    }

    /**
     * 🔄 활성 아티스트만 동기화 (비활성 아티스트는 제외)
     */
    @Transactional
    public void syncActiveArtistsOnly() {
        log.info("🚀 활성 아티스트 동기화 시작");
        
        int pageSize = 100;
        int pageNumber = 0;
        int totalSynced = 0;
        
        try {
            // 기존 인덱스 클리어
            artistSearchRepository.deleteAll();
            log.info("🗑️ 기존 ElasticSearch 인덱스 클리어 완료");
            
            Page<ArtistEntity> artistPage;
            do {
                artistPage = artistRepository.findByIsActiveTrueOrderByNameAsc(PageRequest.of(pageNumber, pageSize));
                
                List<ArtistDocument> documents = artistPage.getContent().stream()
                        .map(this::convertToDocument)
                        .collect(Collectors.toList());
                
                if (!documents.isEmpty()) {
                    artistSearchRepository.saveAll(documents);
                    totalSynced += documents.size();
                    log.info("📦 활성 아티스트 배치 동기화 완료: {} ~ {} (총 {}개)", 
                            pageNumber * pageSize + 1, 
                            pageNumber * pageSize + documents.size(),
                            totalSynced);
                }
                
                pageNumber++;
            } while (artistPage.hasNext());
            
            log.info("🎉 활성 아티스트 동기화 완료! 총 {}개 아티스트 처리", totalSynced);
            
        } catch (Exception e) {
            log.error("❌ 활성 아티스트 동기화 실패: {}", e.getMessage());
            throw new GlobalException("ElasticSearch 동기화 실패", "ELASTICSEARCH_SYNC_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 📊 동기화 상태 확인
     */
    @Transactional(readOnly = true)
    public SyncStatus getSyncStatus() {
        try {
            long dbCount = artistRepository.count();
            long dbActiveCount = artistRepository.countByIsActiveTrue();
            long esCount = artistSearchRepository.count();
            
            return new SyncStatus(dbCount, dbActiveCount, esCount, dbCount == esCount);
        } catch (Exception e) {
            log.error("❌ 동기화 상태 확인 실패: {}", e.getMessage());
            return new SyncStatus(0, 0, 0, false);
        }
    }

    /**
     * 동기화 상태 정보 클래스
     */
    public static class SyncStatus {
        public final long databaseCount;
        public final long databaseActiveCount;
        public final long elasticsearchCount;
        public final boolean inSync;
        
        public SyncStatus(long databaseCount, long databaseActiveCount, long elasticsearchCount, boolean inSync) {
            this.databaseCount = databaseCount;
            this.databaseActiveCount = databaseActiveCount;
            this.elasticsearchCount = elasticsearchCount;
            this.inSync = inSync;
        }
    }
}
