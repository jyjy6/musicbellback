package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LyricsRepository extends JpaRepository<LyricsEntity, Long> {
    
    Optional<LyricsEntity> findByMusicId(Long musicId);
    
    @Query("SELECT l FROM LyricsEntity l WHERE l.music.id = :musicId AND l.isActive = true")
    Optional<LyricsEntity> findActiveLyricsByMusicId(@Param("musicId") Long musicId);
    
    boolean existsByMusicId(Long musicId);
}