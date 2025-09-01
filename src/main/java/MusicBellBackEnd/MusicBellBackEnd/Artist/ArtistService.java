package MusicBellBackEnd.MusicBellBackEnd.Artist;

import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistPageResponseDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistRequestDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistResponseDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.Dto.ArtistSearchDto;
import MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch.ArtistSyncService;
import MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler.GlobalException;
import MusicBellBackEnd.MusicBellBackEnd.Kafka.Producer.ElasticSearchProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistService {
    
    private final ArtistRepository artistRepository;
    private final ArtistSyncService artistSyncService;
    private final ElasticSearchProducerService elasticSearchProducerService;
    
    /**
     * 아티스트명으로 찾기 또는 새로 생성
     * 기존 데이터 마이그레이션을 위한 핵심 메소드
     */
    @Transactional
    public ArtistEntity findOrCreateArtist(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            throw new GlobalException("아티스트명은 필수입니다.", "ARTISTNAME_NEEDED_ERROR", HttpStatus.BAD_REQUEST);
        }
        
        String trimmedName = artistName.trim();
        
        // 1. 정확히 일치하는 아티스트 찾기
        Optional<ArtistEntity> existingArtist = artistRepository.findByNameIgnoreCase(trimmedName);
        if (existingArtist.isPresent()) {
            log.debug("기존 아티스트 발견: {}", trimmedName);
            return existingArtist.get();
        }
        
        // 2. 유사한 이름의 아티스트 체크 (오타 방지)
        List<ArtistEntity> similarArtists = artistRepository.findByNameContainingIgnoreCase(trimmedName);
        if (!similarArtists.isEmpty()) {
            // 정확히 일치하는 것이 있는지 다시 한번 체크
            for (ArtistEntity similar : similarArtists) {
                if (similar.getName().equalsIgnoreCase(trimmedName)) {
                    log.debug("유사 검색에서 정확한 매치 발견: {}", trimmedName);
                    return similar;
                }
            }
            
            // TODO: 나중에 유사도 계산 로직 추가 가능
            log.info("유사한 아티스트 발견되었지만 정확한 매치 없음: {} (유사: {})", 
                    trimmedName, similarArtists.get(0).getName());
        }
        
        // 3. 새 아티스트 생성
        ArtistEntity newArtist = ArtistEntity.builder()
                .name(trimmedName)
                .isVerified(false)
                .isActive(true)
                .followerCount(0L)
                .totalPlayCount(0L)
                .totalLikeCount(0L)
                .build();
                
        ArtistEntity savedArtist = artistRepository.save(newArtist);
        log.info("새 아티스트 생성: {} (ID: {})", trimmedName, savedArtist.getId());

        //추후 Kafka처리
        try {
            elasticSearchProducerService.sendSyncEvent(savedArtist.getId());
        } catch(GlobalException e){
            log.warn("findOrCreateArtist함수 아티스트 통계 ES 동기화 실패: artistId={}, error={}", savedArtist.getId(), e.getMessage());
        }
        
        return savedArtist;
    }
    
    /**
     * 유사한 아티스트명 찾기 (자동완성용)
     */
    public List<ArtistEntity> findSimilarArtists(String partialName, int limit) {
        if (partialName == null || partialName.trim().length() < 2) {
            throw new GlobalException("검색어는 최소 2글자 이상이어야 합니다.", "SEARCH_TERM_TOO_SHORT", HttpStatus.BAD_REQUEST);
        }
        if (limit <= 0 || limit > 50) {
            throw new GlobalException("결과 제한은 1-50 사이여야 합니다.", "INVALID_LIMIT", HttpStatus.BAD_REQUEST);
        }
        
        List<ArtistEntity> similar = artistRepository.findByNameContainingIgnoreCase(partialName.trim());
        return similar.stream()
                .filter(ArtistEntity::getIsActive)
                .limit(limit)
                .toList();
    }
    
    /**
     * 아티스트 통계 업데이트 (음악 재생/좋아요 시 호출)
     */
    @Transactional
    public void updateArtistStats(Long artistId, Long playCountDelta, Long likeCountDelta) {
        if (artistId == null) {
            throw new GlobalException("아티스트 ID는 필수입니다.", "ARTIST_ID_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        
        // 아티스트 존재 여부 확인
        if (!artistRepository.existsById(artistId)) {
            throw new GlobalException("존재하지 않는 아티스트입니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        
        if (playCountDelta != null && playCountDelta != 0) {
            artistRepository.updateTotalPlayCount(artistId, playCountDelta);
        }
        if (likeCountDelta != null && likeCountDelta != 0) {
            artistRepository.updateTotalLikeCount(artistId, likeCountDelta);
        }
        
        // ElasticSearch 동기화 (통계 업데이트 후) Kafka활용
        try {
            elasticSearchProducerService.sendSyncEvent(artistId);
        } catch (Exception e) {
            log.warn("아티스트 통계 ES 동기화 실패: artistId={}, error={}", artistId, e.getMessage());
            // ES 동기화 실패해도 메인 로직은 계속 진행
        }
    }
    
    /**
     * 아티스트 존재 여부 확인
     */
    public boolean existsByName(String name) {
        return artistRepository.existsByNameIgnoreCase(name);
    }
    
    /**
     * 아티스트 조회 (ID로)
     */
    public Optional<ArtistEntity> findById(Long id) {
        return artistRepository.findById(id);
    }

    // === CRUD 메소드들 ===

    /**
     * 아티스트 생성
     */
    @Transactional
    public ArtistResponseDto createArtist(ArtistRequestDto requestDto) {
        // 유효성 검사
        validateArtistRequest(requestDto);

        // 중복 체크
        if (artistRepository.existsByNameIgnoreCase(requestDto.getName())) {
            throw new GlobalException("이미 등록된 아티스트명입니다: " + requestDto.getName(), "ARTIST_NAME_DUPLICATE", HttpStatus.CONFLICT);
        }

        // 엔티티 생성
        ArtistEntity artistEntity = ArtistEntity.builder()
                .name(requestDto.getName().trim())
                .description(requestDto.getDescription())
                .profileImageUrl(requestDto.getProfileImageUrl())
                .genre(requestDto.getGenre())
                .country(requestDto.getCountry())
                .agency(requestDto.getAgency())
                .officialWebsite(requestDto.getOfficialWebsite())
                .instagramHandle(requestDto.getInstagramHandle())
                .twitterHandle(requestDto.getTwitterHandle())
                .spotifyId(requestDto.getSpotifyId())
                .appleMusicId(requestDto.getAppleMusicId())
                .youtubeChannelId(requestDto.getYoutubeChannelId())
                .isVerified(false)
                .isActive(true)
                .followerCount(0L)
                .totalPlayCount(0L)
                .totalLikeCount(0L)
                .build();

        ArtistEntity savedArtist = artistRepository.save(artistEntity);
        log.info("아티스트 생성 완료: {} (ID: {})", savedArtist.getName(), savedArtist.getId());

        // ES 동기화 Kafka활용
        try {
            elasticSearchProducerService.sendSyncEvent(savedArtist.getId());
        } catch (Exception e) {
            log.warn("아티스트 생성 후 ES 동기화 실패: artistId={}, error={}", savedArtist.getId(), e.getMessage());
            // ES 동기화 실패해도 메인 로직은 계속 진행
        }

        return convertToResponseDto(savedArtist);
    }

    /**
     * 아티스트 상세 조회
     */
    public ArtistResponseDto getArtistById(Long id) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        return convertToResponseDto(artist);
    }

    /**
     * 아티스트 목록 조회 (페이징)
     */
    public ArtistPageResponseDto getAllArtists(int page, int size, String sortBy, String sortOrder) {
        try {
            // 입력 검증
            if (page < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (size <= 0 || size > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            Page<ArtistEntity> artistPage = artistRepository.findByIsActiveTrueOrderByNameAsc(pageable);

            return convertToPageResponseDto(artistPage);
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("아티스트 목록 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("아티스트 목록 조회에 실패했습니다.", "ARTIST_LIST_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 아티스트 검색
     */
    public ArtistPageResponseDto searchArtists(ArtistSearchDto searchDto) {
        try {
            // 입력 검증
            if (searchDto.getPage() < 0) {
                throw new GlobalException("페이지 번호는 0 이상이어야 합니다.", "INVALID_PAGE_NUMBER", HttpStatus.BAD_REQUEST);
            }
            if (searchDto.getSize() <= 0 || searchDto.getSize() > 100) {
                throw new GlobalException("페이지 크기는 1-100 사이여야 합니다.", "INVALID_PAGE_SIZE", HttpStatus.BAD_REQUEST);
            }
            
            Sort.Direction direction = "asc".equalsIgnoreCase(searchDto.getSortOrder())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            Pageable pageable = PageRequest.of(
                    searchDto.getPage(),
                    searchDto.getSize(),
                    Sort.by(direction, searchDto.getSortBy())
            );

            Page<ArtistEntity> artistPage = artistRepository.searchArtists(
                    searchDto.getName(),
                    searchDto.getGenre(),
                    searchDto.getCountry(),
                    searchDto.getIsVerified(),
                    pageable
            );

            return convertToPageResponseDto(artistPage);
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("아티스트 검색 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("아티스트 검색에 실패했습니다.", "ARTIST_SEARCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 아티스트 수정
     */
    @Transactional
    public ArtistResponseDto updateArtist(Long id, ArtistRequestDto requestDto) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        // 이름 변경 시 중복 체크
        if (!artist.getName().equalsIgnoreCase(requestDto.getName()) && 
            artistRepository.existsByNameIgnoreCase(requestDto.getName())) {
            throw new GlobalException("이미 등록된 아티스트명입니다: " + requestDto.getName(), "ARTIST_NAME_DUPLICATE", HttpStatus.CONFLICT);
        }
        // 업데이트
        updateArtistEntity(artist, requestDto);
        ArtistEntity savedArtist = artistRepository.save(artist);
        // ES 동기화 Kafka활용
        try {
            elasticSearchProducerService.sendSyncEvent(id);
        } catch (Exception e) {
            log.warn("아티스트 수정 후 ES 동기화 실패: artistId={}, error={}", id, e.getMessage());
            // ES 동기화 실패해도 메인 로직은 계속 진행
        }
        log.info("아티스트 ID {} 정보가 수정되었습니다.", id);
        return convertToResponseDto(savedArtist);
    }
    /**
     * 아티스트 상태 토글 (활성화/비활성화)
     */

    @Transactional
    public boolean toggleArtistStatus(Long id, boolean isActive) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsActive(isActive);
        artistRepository.save(artist);

        log.info("아티스트 ID {} 상태 변경: {}", id, isActive ? "활성화" : "비활성화");
        return isActive;
    }

    /**
     * 아티스트 인증 상태 토글
     */
    @Transactional
    public boolean toggleArtistVerification(Long id, boolean isVerified) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsVerified(isVerified);
        artistRepository.save(artist);

        log.info("아티스트 ID {} 인증 상태 변경: {}", id, isVerified ? "인증" : "인증 해제");
        return isVerified;
    }

    /**
     * 아티스트 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteArtist(Long id) {
        ArtistEntity artist = artistRepository.findById(id)
                .orElseThrow(() -> new GlobalException("아티스트를 찾을 수 없습니다.", "ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND));

        artist.setIsActive(false);
        artistRepository.save(artist);
        //ES 삭제 Kafka활용
        try{
            elasticSearchProducerService.sendDeleteEvent(id);
        } catch(GlobalException e){
            log.warn("아티스트 삭제 후 ES 동기화 실패: artistId={}, error={}", id, e.getMessage());
        }

        log.info("아티스트 ID {}가 삭제되었습니다 (소프트 삭제).", id);
    }

    /**
     * 아티스트 자동완성 제안
     */
    public List<ArtistResponseDto> getArtistSuggestions(String query, int limit) {
        try {
            List<ArtistEntity> suggestions = findSimilarArtists(query, limit);
            return suggestions.stream()
                    .map(this::convertToResponseDto)
                    .collect(Collectors.toList());
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("아티스트 자동완성 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("아티스트 자동완성 조회에 실패했습니다.", "ARTIST_SUGGESTIONS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 인기 아티스트 조회
     */
    public List<ArtistResponseDto> getPopularArtists() {
        try {
            List<ArtistEntity> popularArtists = artistRepository.findTop10ByIsActiveTrueOrderByFollowerCountDesc();
            return popularArtists.stream()
                    .map(this::convertToResponseDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("인기 아티스트 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("인기 아티스트 조회에 실패했습니다.", "POPULAR_ARTISTS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 최신 아티스트 조회
     */
    public List<ArtistResponseDto> getLatestArtists() {
        try {
            List<ArtistEntity> latestArtists = artistRepository.findTop10ByIsActiveTrueOrderByCreatedAtDesc();
            return latestArtists.stream()
                    .map(this::convertToResponseDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("최신 아티스트 조회 중 오류 발생: {}", e.getMessage());
            throw new GlobalException("최신 아티스트 조회에 실패했습니다.", "LATEST_ARTISTS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // === 변환 메소드들 ===

    private ArtistResponseDto convertToResponseDto(ArtistEntity entity) {
        return ArtistResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .profileImageUrl(entity.getProfileImageUrl())
                .genre(entity.getGenre())
                .country(entity.getCountry())
                .agency(entity.getAgency())
                .isVerified(entity.getIsVerified())
                .isActive(entity.getIsActive())
                .followerCount(entity.getFollowerCount())
                .totalPlayCount(entity.getTotalPlayCount())
                .totalLikeCount(entity.getTotalLikeCount())
                .spotifyId(entity.getSpotifyId())
                .appleMusicId(entity.getAppleMusicId())
                .youtubeChannelId(entity.getYoutubeChannelId())
                .officialWebsite(entity.getOfficialWebsite())
                .instagramHandle(entity.getInstagramHandle())
                .twitterHandle(entity.getTwitterHandle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArtistPageResponseDto convertToPageResponseDto(Page<ArtistEntity> page) {
        List<ArtistResponseDto> content = page.getContent().stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());

        return ArtistPageResponseDto.builder()
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

    private void updateArtistEntity(ArtistEntity entity, ArtistRequestDto dto) {
        if (dto.getName() != null) entity.setName(dto.getName().trim());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getProfileImageUrl() != null) entity.setProfileImageUrl(dto.getProfileImageUrl());
        if (dto.getGenre() != null) entity.setGenre(dto.getGenre());
        if (dto.getCountry() != null) entity.setCountry(dto.getCountry());
        if (dto.getAgency() != null) entity.setAgency(dto.getAgency());
        if (dto.getOfficialWebsite() != null) entity.setOfficialWebsite(dto.getOfficialWebsite());
        if (dto.getInstagramHandle() != null) entity.setInstagramHandle(dto.getInstagramHandle());
        if (dto.getTwitterHandle() != null) entity.setTwitterHandle(dto.getTwitterHandle());
        if (dto.getSpotifyId() != null) entity.setSpotifyId(dto.getSpotifyId());
        if (dto.getAppleMusicId() != null) entity.setAppleMusicId(dto.getAppleMusicId());
        if (dto.getYoutubeChannelId() != null) entity.setYoutubeChannelId(dto.getYoutubeChannelId());
    }

    private void validateArtistRequest(ArtistRequestDto requestDto) {
        if (requestDto.getName() == null || requestDto.getName().trim().isEmpty()) {
            throw new GlobalException("아티스트명은 필수입니다.", "ARTIST_NAME_REQUIRED", HttpStatus.BAD_REQUEST);
        }
        if (requestDto.getName().trim().length() < 2) {
            throw new GlobalException("아티스트명은 최소 2글자 이상이어야 합니다.", "ARTIST_NAME_TOO_SHORT", HttpStatus.BAD_REQUEST);
        }
        if (requestDto.getName().trim().length() > 200) {
            throw new GlobalException("아티스트명은 200글자를 초과할 수 없습니다.", "ARTIST_NAME_TOO_LONG", HttpStatus.BAD_REQUEST);
        }
    }
}
