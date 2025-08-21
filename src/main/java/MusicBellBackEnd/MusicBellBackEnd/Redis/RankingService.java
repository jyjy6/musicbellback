package MusicBellBackEnd.MusicBellBackEnd.Redis;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RedisService redisService;
    private static final String DAILY_RANKING_KEY = "ranking:daily:";
    private static final String WEEKLY_RANKING_KEY = "ranking:weekly:";
    private static final String MONTHLY_RANKING_KEY = "ranking:monthly:";

    // 점수 가중치 설정
    private static final int PLAY_SCORE = 1;
    private static final int LIKE_SCORE = 3;
    private static final int DOWNLOAD_SCORE = 2;

    /**
     * 점수 업데이트 (범용 메소드)
     */
    public void updateScore(String table, Long id, int score) {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String thisWeek = getWeekKey();
        String thisMonth = getMonthKey();

        String prefix = table+":";

        // Sorted Set을 사용하여 점수 증가 (더 효율적)
        redisService.incrementScoreInSortedSet(prefix + DAILY_RANKING_KEY + today, id.toString(), score);
        redisService.incrementScoreInSortedSet(prefix + WEEKLY_RANKING_KEY + thisWeek, id.toString(), score);
        redisService.incrementScoreInSortedSet(prefix + MONTHLY_RANKING_KEY + thisMonth, id.toString(), score);

        // TTL 설정 (메모리 최적화)
        redisService.expire(prefix + DAILY_RANKING_KEY + today, 2, TimeUnit.DAYS);
        redisService.expire(prefix + WEEKLY_RANKING_KEY + thisWeek, 8, TimeUnit.DAYS);
        redisService.expire(prefix + MONTHLY_RANKING_KEY + thisMonth, 32, TimeUnit.DAYS);
    }


    /**
     * 점수 타입별 업데이트 메서드 (범용)
     */
    public void updatePlayScore(String table, Long id) {
        updateScore(table, id, PLAY_SCORE);
    }

    public void updateLikeScore(String table, Long id) {
        updateScore(table, id, LIKE_SCORE);
    }

    public void updateDownloadScore(String table, Long id) {
        updateScore(table, id, DOWNLOAD_SCORE);
    }

    /**
     * 상위 랭킹 조회 (개선된 버전)
     */
    public List<Long> getTop(String table, String period, int limit) {
        String key;
        Set<Object> topRankers;

        if (table.equals("music")) {
            key = getRankingKey(period);
        } else {
            key = getRankingKeyForForum(period);
        }
        topRankers = redisService.getTopRanking(key, limit);

        return topRankers.stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
    }

    /**
     * 점수와 함께 랭킹 조회 (범용)
     */
    public List<RankingEntry> getTopWithScores(String table, String period, int limit) {
        String key;
        if (table.equals("music")) {
            key = getRankingKey(period);
        } else {
            key = getRankingKeyForForum(period);
        }

        Set<ZSetOperations.TypedTuple<Object>> rankingWithScores =
                redisService.getRangeWithScores(key, 0, limit - 1);

        return rankingWithScores.stream()
                .map(tuple -> new RankingEntry(
                        Long.valueOf(tuple.getValue().toString()),
                        tuple.getScore().intValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 특정 항목의 랭킹 점수 조회 (범용)
     */
    public Double getScore(String table, Long id, String period) {
        String key;
        if (table.equals("music")) {
            key = getRankingKey(period);
        } else {
            key = getRankingKeyForForum(period);
        }
        return redisService.getScore(key, id.toString());
    }

    /**
     * 기존 이미지 전용 메소드 (하위 호환성 유지)
     */
    public Double getMusicScore(Long musicId, String period) {
        return getScore("music", musicId, period);
    }

    private String getRankingKey(String period) {
        return switch (period.toLowerCase()) {
            case "daily" -> DAILY_RANKING_KEY + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "weekly" -> WEEKLY_RANKING_KEY + getWeekKey();
            default -> DAILY_RANKING_KEY + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        };
    }

    private String getRankingKeyForForum(String period) {
        return switch (period.toLowerCase()) {
            case "daily" -> "forum:"+DAILY_RANKING_KEY + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "weekly" -> "forum:"+WEEKLY_RANKING_KEY + getWeekKey();
            default -> "forum:"+DAILY_RANKING_KEY + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        };
    }

    private String getWeekKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
    }

    private String getMonthKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /**
     * 랭킹 엔트리 클래스 (범용)
     */
    public static class RankingEntry {
        private final Long id;
        private final Integer score;

        public RankingEntry(Long id, Integer score) {
            this.id = id;
            this.score = score;
        }

        public Long getId() { return id; }
        public Long getMusicId() { return id; }
        public Integer getScore() { return score; }
    }
}