package MusicBellBackEnd.MusicBellBackEnd.Redis;

import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecentPlayService {

    private final RedisService redisService;
    private static final String RECENT_PLAY_KEY = "user:recent:music:";
    private final ObjectMapper objectMapper;
    private static final int MAX_RECENT_ITEMS = 10;
    private static final int RECENT_PLAY_TTL = 7; // 7일

    /**
     * 최근 본 항목 추가 (Redis List 직접 활용)
     */
    @Transactional
    public void addRecentPLAY(Long userId, Long musicId, String musicUrl) {
        String key = RECENT_PLAY_KEY + userId;

        try {
            // RecentPLAYItem 객체를 JSON 문자열로 변환
            RecentPLAYItem item = new RecentPLAYItem(musicId, musicUrl);
            String jsonValue = objectMapper.writeValueAsString(item);

            // 기존 동일한 musicId 항목 제거 (중복 방지)
            removeExistingItem(key, musicId);

            // 맨 앞에 추가
            redisService.leftPush(key, jsonValue);

            // 최대 개수 제한
            redisService.trimList(key, 0, MAX_RECENT_ITEMS - 1);

            // TTL 설정
            redisService.expire(key, RECENT_PLAY_TTL, TimeUnit.DAYS);

        } catch (JsonProcessingException e) {
            throw new GlobalException("최근 본 항목 저장 중 오류가 발생했습니다", "RECENT_PLAY_SAVE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * 최근 본 항목에서 특정 이미지 제거
     */
    private void removeExistingItem(String key, Long musicId) {
        List<Object> allItems = redisService.getListRange(key, 0, -1);
        if (allItems != null) {
            for (Object item : allItems) {
                try {
                    RecentPLAYItem recentItem = objectMapper.readValue(item.toString(), RecentPLAYItem.class);
                    if (recentItem.getMusicId().equals(musicId)) {
                        redisService.removeFromList(key, 0, item.toString());
                        break;
                    }
                } catch (JsonProcessingException e) {
                    // 파싱 실패한 항목은 제거
                    redisService.removeFromList(key, 0, item.toString());
                }
            }
        }
    }

    public List<RecentPLAYItem> getRecentPLAYs(Long userId) {
        String key = RECENT_PLAY_KEY + userId;
        List<Object> result = redisService.getListRange(key, 0, MAX_RECENT_ITEMS - 1);

        if (result == null || result.isEmpty()) {
            return new ArrayList<>();
        }

        return result.stream()
                .map(item -> {
                    try {
                        return objectMapper.readValue(item.toString(), RecentPLAYItem.class);
                    } catch (JsonProcessingException e) {
                        return null; // 파싱 실패한 항목은 null로 처리
                    }
                })
                .filter(Objects::nonNull) // null 제거
                .collect(Collectors.toList());
    }


    /**
     * 사용자의 최근 본 항목 전체 삭제
     */
    public void clearRecentPLAYs(Long userId) {
        String key = RECENT_PLAY_KEY + userId;
        redisService.deleteValue(key);
    }

    /**
     * 최근 본 항목 개수 조회
     */
    public int getRecentPLAYCount(Long userId) {
        return getRecentPLAYs(userId).size();
    }

    /**
     * 배치로 여러 항목 추가 (성능 최적화)
     */
    public void addMultipleRecentPLAYs(Long userId, List<Long> musicIds) {
        String key = RECENT_PLAY_KEY + userId;

        for (Long musicId : musicIds) {
            // 중복 제거
            redisService.removeFromList(key, 0, musicId.toString());
            // 앞에 추가
            redisService.leftPush(key, musicId.toString());
        }

        // 최대 개수 제한
        redisService.trimList(key, 0, MAX_RECENT_ITEMS - 1);

        // TTL 설정
        redisService.expire(key, RECENT_PLAY_TTL, TimeUnit.DAYS);
    }
}



@Data
@AllArgsConstructor
@NoArgsConstructor
class RecentPLAYItem {
    
    private Long musicId;
    private String musicUrl;
}