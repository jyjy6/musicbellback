package MusicBellBackEnd.MusicBellBackEnd.Lyrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LyricsLineRepository extends JpaRepository<LyricsLineEntity, Long> {
    
    List<LyricsLineEntity> findByLyricsIdOrderByLineOrder(Long lyricsId);
    
    @Query("SELECT ll FROM LyricsLineEntity ll WHERE ll.lyrics.id = :lyricsId ORDER BY ll.startTime ASC")
    List<LyricsLineEntity> findByLyricsIdOrderByStartTime(@Param("lyricsId") Long lyricsId);
    
    void deleteByLyricsId(Long lyricsId);
}