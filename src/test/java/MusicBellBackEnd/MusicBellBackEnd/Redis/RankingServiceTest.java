package MusicBellBackEnd.MusicBellBackEnd.Redis;


import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistService;
import MusicBellBackEnd.MusicBellBackEnd.Music.MusicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RankingServiceTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private RankingService rankingService;

    private static final String DAILY_RANKING_KEY = "ranking:daily:";
    private static final String WEEKLY_RANKING_KEY = "ranking:weekly:";
    private static final String MONTHLY_RANKING_KEY = "ranking:monthly:";

    // 점수 가중치 설정
    private static final int PLAY_SCORE = 1;
    private static final int LIKE_SCORE = 3;
    private static final int DOWNLOAD_SCORE = 2;

    
    @Test
    void updateScore_Update_DAILY_WEEKLY_MONTHLY_RANKING(){
        //given
        String table = "music";
        Long id = 1L;
        int score = 3;


        //when
        rankingService.updateScore(table,id,score);
        // then
        verify(redisService, times(1))
                .incrementScoreInSortedSet(startsWith("music:ranking:daily:"), eq("1"), eq(3.0));
        verify(redisService, times(1))
                .incrementScoreInSortedSet(startsWith("music:ranking:weekly:"), eq("1"), eq(3.0));
        verify(redisService, times(1))
                .incrementScoreInSortedSet(startsWith("music:ranking:monthly:"), eq("1"), eq(3.0));

        verify(redisService, times(1)).expire(startsWith("music:ranking:daily"), eq(2L), eq(TimeUnit.DAYS));
        verify(redisService, times(1)).expire(startsWith("music:ranking:weekly"), eq(8L), eq(TimeUnit.DAYS));
        verify(redisService, times(1)).expire(startsWith("music:ranking:monthly"), eq(32L), eq(TimeUnit.DAYS));
    }


}
