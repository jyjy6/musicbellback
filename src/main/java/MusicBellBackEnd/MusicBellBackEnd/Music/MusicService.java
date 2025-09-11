package MusicBellBackEnd.MusicBellBackEnd.Music;

import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistEntity;
import MusicBellBackEnd.MusicBellBackEnd.Artist.ArtistService;
import MusicBellBackEnd.MusicBellBackEnd.Auth.CustomUserDetails;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Music.Dto.*;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RankingService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RecentPlayService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.RedisService;
import MusicBellBackEnd.MusicBellBackEnd.Redis.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@Service
public class MusicService {
    private final S3Client s3Client;
    private final MusicRepository musicRepository;
    private final ArtistService artistService;
    private final RedisService redisService;
    private final RankingService rankingService;
    private final PlaylistService playlistService;
    private final RecentPlayService recentPlayService;


    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;
    private final S3Presigner s3Presigner;
    
    String createPresignedUrl(String path) {
        var putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket) //올릴 버킷명
                .key(path) //경로
                .build();
        var preSignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60)) //URL 유효기간
                .putObjectRequest(putObjectRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(preSignRequest).url().toString();
        return presignedUrl;
    }

    // 음악 업로드 (다중)
    @Transactional
    public List<MusicResponseDto> uploadMusics(List<MusicRequestDto> musicRequestDtos, Authentication auth) {

        Long uploaderId = ((CustomUserDetails)auth.getPrincipal()).getId();

        try {
            List<MusicEntity> savedMusics = musicRequestDtos.stream()
                    .map(this::convertToEntity)
                    .peek(entity -> entity.setUploaderId(uploaderId))
                    .map(musicRepository::save)
                    .collect(Collectors.toList());
            
            log.info("총 {}개의 음악이 업로드되었습니다.", savedMusics.size());
            return savedMusics.stream()
                    .map(this::convertToResponseDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("음악 업로드 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("음악 업로드에 실패했습니다.", "MUSIC_UPLOAD_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 음악 상세 조회
    public MusicResponseDto getMusicById(Long id, Authentication auth) {
        MusicEntity music = musicRepository.findById(id)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "MUSIC_NOT_FOUND", HttpStatus.NOT_FOUND));
        //추후 Kafka로 비동기처리
        this.incrementPlayCount(id);

        // 로그인된 사용자의 플레이리스트에 자동 추가
        if(auth != null && auth.isAuthenticated()){
            try {
                Long userId = ((CustomUserDetails) auth.getPrincipal()).getId();
                playlistService.addToPlaylist(userId, music.getId(), music.getTitle(), music.getMusicUrl());
                //레디스 최근재생목록 추가(추후 Kafka 비동기처리 고려)
                recentPlayService.addRecentPlay(userId, music.getId(), music.getTitle(), music.getAlbumImageUrl(), music.getArtist(), music.getDuration());
                log.info("사용자 ID {}의 플레이리스트에 음악 ID {} 추가됨", userId, music.getId());
            } catch (Exception e) {
                log.warn("플레이리스트 추가 중 오류 발생: {}", e.getMessage());
                // 플레이리스트 추가 실패해도 음악 조회는 정상 진행
            }
        }

        return convertToResponseDto(music);
    }

    // 음악 정보만 조회 (재생 카운트 증가 없음)
    public MusicResponseDto getMusicByIdWithoutIncrement(Long id) {
        MusicEntity music = musicRepository.findById(id)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "MUSIC_NOT_FOUND", HttpStatus.NOT_FOUND));

        return convertToResponseDto(music);
    }

    // 음악 목록 조회 (페이징)
    public MusicPageResponseDto getAllMusics(int page, int size, String sortBy, String sortOrder) {
        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            Page<MusicEntity> musicPage = musicRepository.findByIsPublicTrueOrderByCreatedAtDesc(pageable);
            
            return convertToPageResponseDto(musicPage);
        } catch (Exception e) {
            log.error("음악 목록 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("음악 목록 조회에 실패했습니다.", "MUSIC_LIST_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 음악 검색
    public MusicPageResponseDto searchMusics(MusicSearchDto searchDto) {
        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(searchDto.getSortOrder()) 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            
            Pageable pageable = PageRequest.of(
                    searchDto.getPage(), 
                    searchDto.getSize(), 
                    Sort.by(direction, searchDto.getSortBy())
            );
            
            Page<MusicEntity> musicPage = musicRepository.searchMusic(
                    searchDto.getTitle(),
                    searchDto.getArtist(),
                    searchDto.getAlbum(),
                    searchDto.getGenre(),
                    searchDto.getUploaderName(),
                    searchDto.getMusicGrade(),
                    pageable
            );
            
            return convertToPageResponseDto(musicPage);
        } catch (Exception e) {
            log.error("음악 검색 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("음악 검색에 실패했습니다.", "MUSIC_SEARCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 음악 정보 수정
    @Transactional
    public MusicResponseDto updateMusic(Long id, MusicUpdateDto updateDto, String uploaderName) {
        MusicEntity music = musicRepository.findById(id)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "MUSIC_NOT_FOUND", HttpStatus.NOT_FOUND));
        
        // 업로더 권한 확인
        if (!music.getUploaderName().equals(uploaderName)) {
            throw new GlobalException("음악 수정 권한이 없습니다.", "MUSIC_UPDATE_FORBIDDEN", HttpStatus.FORBIDDEN);
        }
        
        // 업데이트
        updateMusicEntity(music, updateDto);
        MusicEntity savedMusic = musicRepository.save(music);
        
        log.info("음악 ID {} 정보가 수정되었습니다.", id);
        return convertToResponseDto(savedMusic);
    }

    // 음악 삭제
    @Transactional
    public void deleteMusic(Long id, String uploaderName) {
        MusicEntity music = musicRepository.findById(id)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "MUSIC_NOT_FOUND", HttpStatus.NOT_FOUND));
        
        // 업로더 권한 확인
        if (!music.getUploaderName().equals(uploaderName)) {
            throw new GlobalException("음악 삭제 권한이 없습니다.", "MUSIC_DELETE_FORBIDDEN", HttpStatus.FORBIDDEN);
        }
        
        musicRepository.delete(music);
        log.info("음악 ID {}가 삭제되었습니다.", id);
    }

    // 좋아요 토글
    @Transactional
    public boolean toggleLike(Long id, boolean isLike) {
        MusicEntity music = musicRepository.findById(id)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "MUSIC_NOT_FOUND", HttpStatus.NOT_FOUND));
        
        if (isLike) {
            musicRepository.incrementLikeCount(id);
            log.info("음악 ID {} 좋아요가 증가되었습니다.", id);
            
            // 아티스트 totalLikeCount 증가
            if (music.getArtistEntity() != null) {
                Long artistId = music.getArtistEntity().getId();
                artistService.updateArtistStats(artistId, null, 1L);
                log.info("아티스트 ID {} 좋아요가 증가되었습니다.", artistId);
            }
        } else {
            musicRepository.decrementLikeCount(id);
            log.info("음악 ID {} 좋아요가 감소되었습니다.", id);
            
            // 아티스트 totalLikeCount 감소
            if (music.getArtistEntity() != null) {
                Long artistId = music.getArtistEntity().getId();
                artistService.updateArtistStats(artistId, null, -1L);
                log.info("아티스트 ID {} 좋아요가 감소되었습니다.", artistId);
            }
        }
        
        return isLike;
    }

    // 인기 음악 조회
    public List<MusicStatsDto> getPopularMusics() {
        List<MusicEntity> popularMusics = musicRepository.findTop10ByIsPublicTrueOrderByPlayCountDesc();
        return popularMusics.stream()
                .map(this::convertToStatsDto)
                .collect(Collectors.toList());
    }

    // 최신 음악 조회
    public List<MusicStatsDto> getLatestMusics() {
        List<MusicEntity> latestMusics = musicRepository.findTop10ByIsPublicTrueOrderByCreatedAtDesc();
        return latestMusics.stream()
                .map(this::convertToStatsDto)
                .collect(Collectors.toList());
    }

    // === 변환 메서드들 ===
    
    private MusicEntity convertToEntity(MusicRequestDto dto) {
        // String artist를 ArtistEntity로 변환 (기존 데이터와의 호환성 유지)
        ArtistEntity artistEntity = null;
        if (dto.getArtist() != null && !dto.getArtist().trim().isEmpty()) {
            artistEntity = artistService.findOrCreateArtist(dto.getArtist());
        }
        
        return MusicEntity.builder()
                .title(dto.getTitle())
                .artist(dto.getArtist()) // 기존 String 필드 유지 (점진적 전환)
                .artistEntity(artistEntity) // 새로운 ArtistEntity 관계 설정
                .album(dto.getAlbum())
                .genre(dto.getGenre())
                .releaseDate(dto.getReleaseDate())
                .duration(dto.getDuration())
                .musicUrl(dto.getMusicUrl())
                .albumImageUrl(dto.getAlbumImageUrl())
                .uploaderName(dto.getUploaderName())
                .isPublic(dto.getIsPublic() != null ? dto.getIsPublic() : true)
                .fileSize(dto.getFileSize())
                .fileType(dto.getFileType())
                .musicGrade(dto.getMusicGrade() != null ? dto.getMusicGrade() : "GENERAL")
                .playCount(0L)
                .likeCount(0L)
                .build();
    }

    private MusicResponseDto convertToResponseDto(MusicEntity entity) {
        return MusicResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .artist(entity.getArtist())
                .album(entity.getAlbum())
                .genre(entity.getGenre())
                .releaseDate(entity.getReleaseDate())
                .duration(entity.getDuration())
                .musicUrl(entity.getMusicUrl())
                .albumImageUrl(entity.getAlbumImageUrl())
                .uploaderName(entity.getUploaderName())
                .playCount(entity.getPlayCount())
                .likeCount(entity.getLikeCount())
                .isPublic(entity.getIsPublic())
                .fileSize(entity.getFileSize())
                .fileType(entity.getFileType())
                .musicGrade(entity.getMusicGrade())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private MusicPageResponseDto convertToPageResponseDto(Page<MusicEntity> page) {
        List<MusicResponseDto> content = page.getContent().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return MusicPageResponseDto.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    private MusicStatsDto convertToStatsDto(MusicEntity entity) {
        return MusicStatsDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .artist(entity.getArtist())
                .albumImageUrl(entity.getAlbumImageUrl())
                .playCount(entity.getPlayCount())
                .likeCount(entity.getLikeCount())
                .build();
    }

    private void updateMusicEntity(MusicEntity entity, MusicUpdateDto dto) {
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getArtist() != null) entity.setArtist(dto.getArtist());
        if (dto.getAlbum() != null) entity.setAlbum(dto.getAlbum());
        if (dto.getGenre() != null) entity.setGenre(dto.getGenre());
        if (dto.getReleaseDate() != null) entity.setReleaseDate(dto.getReleaseDate());
        if (dto.getDuration() != null) entity.setDuration(dto.getDuration());
        if (dto.getAlbumImageUrl() != null) entity.setAlbumImageUrl(dto.getAlbumImageUrl());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getMusicGrade() != null) entity.setMusicGrade(dto.getMusicGrade());
    }

    // 여러 음악 ID로 배치 조회 (랭킹용)
    public List<MusicResponseDto> getMusicsByIds(List<Long> musicIds) {
        try {
            List<MusicEntity> musics = musicRepository.findAllById(musicIds);
            
            // 원래 순서 유지를 위해 ID 순서대로 정렬
            return musicIds.stream()
                    .map(id -> musics.stream()
                            .filter(music -> music.getId().equals(id))
                            .findFirst()
                            .map(this::convertToResponseDto)
                            .orElse(null))
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("배치 음악 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("배치 음악 조회에 실패했습니다.", "BATCH_MUSIC_FETCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Transactional
    public void incrementPlayCount(Long musicId) {
        // DB 업데이트
        MusicEntity music = musicRepository.findById(musicId)
                .orElseThrow(() -> new GlobalException("음악을 찾을 수 없습니다.", "NOT_MUSIC_FOUND", HttpStatus.NOT_FOUND));
        music.setPlayCount(music.getPlayCount() + 1);
        musicRepository.save(music);

        // 아티스트 totalPlayCount 증가
        if (music.getArtistEntity() != null) {
            Long artistId = music.getArtistEntity().getId();
            artistService.updateArtistStats(artistId, 1L, null);
            log.info("아티스트 ID {} 재생수가 증가되었습니다.", artistId);
        }

        // Redis 캐시 업데이트
        redisService.incrementHashValue("music:stats:" + musicId, "playCount", 1);
        
        // 랭킹 점수 업데이트
        rankingService.updatePlayScore("music", musicId);
    }
}
