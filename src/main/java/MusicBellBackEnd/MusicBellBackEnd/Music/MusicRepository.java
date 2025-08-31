package MusicBellBackEnd.MusicBellBackEnd.Music;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicRepository extends JpaRepository<MusicEntity, Long> {
    
    // 공개된 음악만 조회
    Page<MusicEntity> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);
    
    // 업로더별 음악 조회
    Page<MusicEntity> findByUploaderNameAndIsPublicTrueOrderByCreatedAtDesc(String uploaderName, Pageable pageable);
    
    // 제목으로 검색 (공개된 음악만)
    Page<MusicEntity> findByTitleContainingIgnoreCaseAndIsPublicTrueOrderByCreatedAtDesc(String title, Pageable pageable);
    
    // 아티스트로 검색 (공개된 음악만)
    Page<MusicEntity> findByArtistContainingIgnoreCaseAndIsPublicTrueOrderByCreatedAtDesc(String artist, Pageable pageable);
    
    // 장르로 검색 (공개된 음악만)
    Page<MusicEntity> findByGenreAndIsPublicTrueOrderByCreatedAtDesc(String genre, Pageable pageable);
    
    // 복합 검색
    @Query("SELECT m FROM MusicEntity m WHERE " +
           "(:title IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:artist IS NULL OR m.artistEntity.id = :artist) AND " +
           "(:album IS NULL OR LOWER(m.album) LIKE LOWER(CONCAT('%', :album, '%'))) AND " +
           "(:genre IS NULL OR m.genre = :genre) AND " +
           "(:uploaderName IS NULL OR m.uploaderName = :uploaderName) AND " +
           "(:musicGrade IS NULL OR m.musicGrade = :musicGrade) AND " +
           "m.isPublic = true")
    Page<MusicEntity> searchMusic(@Param("title") String title,
                                  @Param("artist") Long artist,
                                  @Param("album") String album,
                                  @Param("genre") String genre,
                                  @Param("uploaderName") String uploaderName,
                                  @Param("musicGrade") String musicGrade,
                                  Pageable pageable);
    
    // 재생수 TOP 음악
    List<MusicEntity> findTop10ByIsPublicTrueOrderByPlayCountDesc();
    
    // 좋아요 TOP 음악
    List<MusicEntity> findTop10ByIsPublicTrueOrderByLikeCountDesc();
    
    // 최신 음악
    List<MusicEntity> findTop10ByIsPublicTrueOrderByCreatedAtDesc();
    
    // 재생수 증가
    @Modifying
    @Query("UPDATE MusicEntity m SET m.playCount = m.playCount + 1 WHERE m.id = :id")
    void incrementPlayCount(@Param("id") Long id);
    
    // 좋아요 수 증가
    @Modifying
    @Query("UPDATE MusicEntity m SET m.likeCount = m.likeCount + 1 WHERE m.id = :id")
    void incrementLikeCount(@Param("id") Long id);
    
    // 좋아요 수 감소
    @Modifying
    @Query("UPDATE MusicEntity m SET m.likeCount = m.likeCount - 1 WHERE m.id = :id AND m.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
    
    // === 마이그레이션 관련 쿼리 ===
    
    // 마이그레이션이 필요한 음악 조회 (기존 artist는 있지만 artistEntity가 null)
    @Query("SELECT m FROM MusicEntity m WHERE m.artist IS NOT NULL AND m.artistEntity IS NULL")
    Page<MusicEntity> findMusicNeedingMigration(Pageable pageable);
    
    // 마이그레이션된 음악 개수
    @Query("SELECT COUNT(m) FROM MusicEntity m WHERE m.artistEntity IS NOT NULL")
    long countMigratedMusic();
    
    // 마이그레이션되지 않은 음악 개수
    @Query("SELECT COUNT(m) FROM MusicEntity m WHERE m.artist IS NOT NULL AND m.artistEntity IS NULL")
    long countUnmigratedMusic();
}