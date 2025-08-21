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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final RedisService redisService;
    private static final String PLAYLIST_KEY = "user:playlist:";
    private final ObjectMapper objectMapper;

    /**
     * 플레이리스트에 음악 추가
     */
    @Transactional
    public void addToPlaylist(Long userId, Long musicId, String musicTitle, String musicUrl) {
        String key = PLAYLIST_KEY + userId;

        try {
            // PlaylistItem 객체를 JSON 문자열로 변환
            PlaylistItem item = new PlaylistItem(musicId, musicTitle, musicUrl, LocalDateTime.now());
            String jsonValue = objectMapper.writeValueAsString(item);

            // 맨 뒤에 추가 (재생 순서 유지)
            redisService.rightPush(key, jsonValue);

        } catch (JsonProcessingException e) {
            throw new GlobalException("플레이리스트 저장 중 오류가 발생했습니다", "PLAYLIST_SAVE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 플레이리스트에서 특정 위치의 음악 제거
     */
    @Transactional
    public void removeFromPlaylist(Long userId, int index) {
        String key = PLAYLIST_KEY + userId;
        List<Object> playlist = redisService.getListRange(key, 0, -1);
        
        if (playlist == null || index < 0 || index >= playlist.size()) {
            throw new GlobalException("잘못된 플레이리스트 인덱스입니다", "INVALID_PLAYLIST_INDEX", HttpStatus.BAD_REQUEST);
        }

        Object itemToRemove = playlist.get(index);
        redisService.removeFromList(key, 1, itemToRemove);
    }

    /**
     * 플레이리스트에서 특정 musicId의 첫 번째 항목 제거
     */
    @Transactional
    public void removeFromPlaylistByMusicId(Long userId, Long musicId) {
        String key = PLAYLIST_KEY + userId;
        List<Object> allItems = redisService.getListRange(key, 0, -1);
        
        if (allItems != null) {
            for (Object item : allItems) {
                try {
                    PlaylistItem playlistItem = objectMapper.readValue(item.toString(), PlaylistItem.class);
                    if (playlistItem.getMusicId().equals(musicId)) {
                        redisService.removeFromList(key, 1, item.toString());
                        break;
                    }
                } catch (JsonProcessingException e) {
                    // 파싱 실패한 항목은 제거
                    redisService.removeFromList(key, 1, item.toString());
                }
            }
        }
    }

    /**
     * 사용자의 플레이리스트 조회
     */
    public List<PlaylistItem> getPlaylist(Long userId) {
        String key = PLAYLIST_KEY + userId;
        List<Object> result = redisService.getListRange(key, 0, -1);

        if (result == null || result.isEmpty()) {
            return new ArrayList<>();
        }

        return result.stream()
                .map(item -> {
                    try {
                        return objectMapper.readValue(item.toString(), PlaylistItem.class);
                    } catch (JsonProcessingException e) {
                        return null; // 파싱 실패한 항목은 null로 처리
                    }
                })
                .filter(Objects::nonNull) // null 제거
                .collect(Collectors.toList());
    }

    /**
     * 플레이리스트의 특정 범위 조회 (페이징)
     */
    public List<PlaylistItem> getPlaylistRange(Long userId, int start, int end) {
        String key = PLAYLIST_KEY + userId;
        List<Object> result = redisService.getListRange(key, start, end);

        if (result == null || result.isEmpty()) {
            return new ArrayList<>();
        }

        return result.stream()
                .map(item -> {
                    try {
                        return objectMapper.readValue(item.toString(), PlaylistItem.class);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 플레이리스트 전체 삭제
     */
    @Transactional
    public void clearPlaylist(Long userId) {
        String key = PLAYLIST_KEY + userId;
        redisService.deleteValue(key);
    }

    /**
     * 플레이리스트 개수 조회
     */
    public int getPlaylistCount(Long userId) {
        return getPlaylist(userId).size();
    }

    /**
     * 플레이리스트 순서 변경 (특정 인덱스의 항목을 다른 위치로 이동)
     */
    @Transactional
    public void movePlaylistItem(Long userId, int fromIndex, int toIndex) {
        String key = PLAYLIST_KEY + userId;
        List<Object> playlist = redisService.getListRange(key, 0, -1);
        
        if (playlist == null || fromIndex < 0 || fromIndex >= playlist.size() || 
            toIndex < 0 || toIndex >= playlist.size()) {
            throw new GlobalException("잘못된 플레이리스트 인덱스입니다", "INVALID_PLAYLIST_INDEX", HttpStatus.BAD_REQUEST);
        }

        // 기존 플레이리스트를 리스트로 변환
        List<PlaylistItem> items = getPlaylist(userId);
        
        // 항목 이동
        PlaylistItem item = items.remove(fromIndex);
        items.add(toIndex, item);
        
        // 플레이리스트 재구성
        clearPlaylist(userId);
        for (PlaylistItem playlistItem : items) {
            addToPlaylist(userId, playlistItem.getMusicId(), playlistItem.getMusicTitle(), playlistItem.getMusicUrl());
        }
    }

    /**
     * 플레이리스트에 특정 음악이 있는지 확인
     */
    public boolean isInPlaylist(Long userId, Long musicId) {
        List<PlaylistItem> playlist = getPlaylist(userId);
        return playlist.stream().anyMatch(item -> item.getMusicId().equals(musicId));
    }

    /**
     * 배치로 여러 음악을 플레이리스트에 추가
     */
    @Transactional
    public void addMultipleToPlaylist(Long userId, List<PlaylistItem> items) {
        String key = PLAYLIST_KEY + userId;

        try {
            for (PlaylistItem item : items) {
                String jsonValue = objectMapper.writeValueAsString(item);
                redisService.rightPush(key, jsonValue);
            }
        } catch (JsonProcessingException e) {
            throw new GlobalException("플레이리스트 배치 저장 중 오류가 발생했습니다", "PLAYLIST_BATCH_SAVE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}