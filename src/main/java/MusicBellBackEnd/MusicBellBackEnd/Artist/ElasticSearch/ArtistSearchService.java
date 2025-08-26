package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistSearchService {

    private final ArtistSearchRepository artistSearchRepository;
    private final ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 🎯 스마트 아티스트 검색 (페이지네이션 지원)
     * - 아티스트명, 소개, 소속사, 장르에서 검색
     * - 오타 허용, 부분 검색 지원
     * - 인기도 기반 정렬
     * - 페이지네이션 지원
     */
    public Page<ArtistDocument> smartSearch(String keyword, String genre, String country, Boolean isVerified, int page, int size) {
        try {
            log.info("🚀 아티스트 검색 시작: keyword={}, genre={}, country={}, verified={}, page={}, size={}", 
                    keyword, genre, country, isVerified, page, size);
            
            Pageable pageable = PageRequest.of(page, size);
            
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        // should 조건들 (OR 검색)
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            b.should(s -> s.multiMatch(m -> m
                                    .query(keyword)
                                    .fields("name^3", "description^2", "searchText^1.5") // 아티스트명에 가장 높은 가중치
                                    .boost(3.0f)
                            ))
                            // 오타 허용 검색
                            .should(s -> s.multiMatch(m -> m
                                    .query(keyword)
                                    .fields("name^2", "description^1.5", "agency^1")
                                    .fuzziness("AUTO") // 오타 허용
                                    .boost(2.0f)
                            ))
                            // 부분 검색 (와일드카드)
                            .should(s -> s.wildcard(w -> w
                                    .field("name")
                                    .value("*" + keyword.toLowerCase() + "*")
                                    .boost(1.5f)
                            ))
                            // 소속사 검색
                            .should(s -> s.match(m -> m
                                    .field("agency")
                                    .query(keyword)
                                    .boost(1.0f)
                            ))
                            .minimumShouldMatch("1"); // 최소 하나는 매치
                        }

                        // must 조건들 (AND 필터)
                        // 활성 아티스트만 검색
                        b.must(m -> m.term(t -> t.field("isActive").value(true)));
                        
                        if (genre != null) {
                            b.must(m -> m.term(t -> t.field("genre").value(genre)));
                        }
                        if (country != null) {
                            b.must(m -> m.term(t -> t.field("country").value(country)));
                        }
                        if (isVerified != null) {
                            b.must(m -> m.term(t -> t.field("isVerified").value(isVerified)));
                        }

                        return b;
                    }))
                    .withSort(Sort.by(Sort.Direction.DESC, "_score")) // 관련도 순 (1차)
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore")) // 인기도 순 (2차)
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount")) // 팔로워 순 (3차)
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);

            List<ArtistDocument> results = searchHits.stream()
                    .map(hit -> {
                        ArtistDocument artist = hit.getContent();
                        log.info("🎯 점수: {:.2f} - 아티스트: {} (장르: {})",
                                hit.getScore(), artist.getName(), artist.getGenre());
                        return artist;
                    })
                    .collect(Collectors.toList());

            Page<ArtistDocument> resultPage = new PageImpl<>(results, pageable, searchHits.getTotalHits());

            log.info("🚀 아티스트 검색 완료: {} 개 결과 (전체: {}, 페이지: {}/{})", 
                    results.size(), searchHits.getTotalHits(), page + 1, resultPage.getTotalPages());
            
            return resultPage;

        } catch (Exception e) {
            log.error("🚨 아티스트 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * 🎵 장르별 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> searchByGenre(String genre, int page, int size) {
        try {
            log.info("🎵 장르별 검색: {}, page={}, size={}", genre, page, size);

            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("genre").value(genre)))
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("🚨 장르별 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * 🏆 인기 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getPopularArtists(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("🚨 인기 아티스트 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * ✅ 인증 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getVerifiedArtists(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("isVerified").value(true)))
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("🚨 인증 아티스트 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * 🆕 신규 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> getRecentArtists(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "createdAt"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("🚨 신규 아티스트 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * 🌍 국가별 아티스트 검색 (페이지네이션 지원)
     */
    public Page<ArtistDocument> searchByCountry(String country, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("country").value(country)))
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withSort(Sort.by(Sort.Direction.DESC, "followerCount"))
                    .withPageable(pageable)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            List<ArtistDocument> results = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("🚨 국가별 검색 실패: ", e);
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(page, size), 0);
        }
    }

    /**
     * 🔍 아티스트명 자동완성
     */
    public List<String> autoComplete(String prefix, int size) {
        try {
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.prefix(p -> p
                                    .field("name")
                                    .value(prefix.toLowerCase())
                            ))
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withSort(Sort.by(Sort.Direction.DESC, "popularityScore"))
                    .withMaxResults(size)
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            return searchHits.stream()
                    .map(hit -> hit.getContent().getName())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("🚨 자동완성 실패: ", e);
            return new ArrayList<>();
        }
    }

    /**
     * 📊 장르별 통계 조회
     */
    public List<GenreStats> getGenreStats() {
        try {
            // 이 부분은 집계 쿼리로 구현할 수 있지만, 간단히 모든 활성 아티스트를 가져와서 처리
            Query query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("isActive").value(true)))
                    ))
                    .withMaxResults(10000) // 충분히 큰 수
                    .build();

            SearchHits<ArtistDocument> searchHits = elasticsearchTemplate.search(query, ArtistDocument.class);
            
            return searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.groupingBy(
                            artist -> artist.getGenre() != null ? artist.getGenre() : "기타",
                            Collectors.counting()
                    ))
                    .entrySet().stream()
                    .map(entry -> new GenreStats(entry.getKey(), entry.getValue()))
                    .sorted((a, b) -> Long.compare(b.count, a.count))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("🚨 장르별 통계 조회 실패: ", e);
            return new ArrayList<>();
        }
    }

    /**
     * 장르별 통계 정보 클래스
     */
    public static class GenreStats {
        public final String genre;
        public final long count;
        
        public GenreStats(String genre, long count) {
            this.genre = genre;
            this.count = count;
        }
    }
}
