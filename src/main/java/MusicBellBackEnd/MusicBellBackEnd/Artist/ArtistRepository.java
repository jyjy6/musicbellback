package MusicBellBackEnd.MusicBellBackEnd.Artist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<ArtistEntity, Long> {
    
    // 이름으로 정확히 찾기 (대소문자 무시)
    Optional<ArtistEntity> findByNameIgnoreCase(String name);
    
    // 이름으로 검색 (LIKE, 대소문자 무시)
    List<ArtistEntity> findByNameContainingIgnoreCase(String name);
    
    // 활성 아티스트만 조회
    Page<ArtistEntity> findByIsActiveTrueOrderByNameAsc(Pageable pageable);
    
    // 인증된 아티스트만 조회
    Page<ArtistEntity> findByIsVerifiedTrueAndIsActiveTrueOrderByFollowerCountDesc(Pageable pageable);
    
    // 장르별 아티스트 조회
    Page<ArtistEntity> findByGenreAndIsActiveTrueOrderByFollowerCountDesc(String genre, Pageable pageable);
    
    // 인기 아티스트 TOP N
    List<ArtistEntity> findTop10ByIsActiveTrueOrderByFollowerCountDesc();
    
    // 최신 등록 아티스트
    List<ArtistEntity> findTop10ByIsActiveTrueOrderByCreatedAtDesc();
    
    // 아티스트명 중복 체크
    boolean existsByNameIgnoreCase(String name);
    
    // 팔로워 수 증가
    @Modifying
    @Query("UPDATE ArtistEntity a SET a.followerCount = a.followerCount + 1 WHERE a.id = :id")
    void incrementFollowerCount(@Param("id") Long id);
    
    // 팔로워 수 감소
    @Modifying
    @Query("UPDATE ArtistEntity a SET a.followerCount = a.followerCount - 1 WHERE a.id = :id AND a.followerCount > 0")
    void decrementFollowerCount(@Param("id") Long id);
    
    // 총 재생수 업데이트
    @Modifying
    @Query("UPDATE ArtistEntity a SET a.totalPlayCount = a.totalPlayCount + :count WHERE a.id = :id")
    void updateTotalPlayCount(@Param("id") Long id, @Param("count") Long count);
    
    // 총 좋아요 수 업데이트
    @Modifying
    @Query("UPDATE ArtistEntity a SET a.totalLikeCount = a.totalLikeCount + :count WHERE a.id = :id")
    void updateTotalLikeCount(@Param("id") Long id, @Param("count") Long count);
    
    // 아티스트 검색 (복합 검색)
    @Query("SELECT a FROM ArtistEntity a WHERE " +
           "(:name IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:genre IS NULL OR a.genre = :genre) AND " +
           "(:country IS NULL OR a.country = :country) AND " +
           "(:isVerified IS NULL OR a.isVerified = :isVerified) AND " +
           "a.isActive = true")
    Page<ArtistEntity> searchArtists(@Param("name") String name,
                                    @Param("genre") String genre,
                                    @Param("country") String country,
                                    @Param("isVerified") Boolean isVerified,
                                    Pageable pageable);
}
