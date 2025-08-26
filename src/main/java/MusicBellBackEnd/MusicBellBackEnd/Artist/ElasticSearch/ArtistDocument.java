package MusicBellBackEnd.MusicBellBackEnd.Artist.ElasticSearch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "artists")
public class ArtistDocument {

    @Id
    private String id;

    // 기본 아티스트 정보
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name; // 아티스트명 (검색의 핵심)

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description; // 아티스트 소개

    @Field(type = FieldType.Keyword)
    private String profileImageUrl; // 프로필 이미지 URL

    // 분류/장르 정보
    @Field(type = FieldType.Keyword)
    private String genre; // 주 장르 (필터링용)

    @Field(type = FieldType.Keyword)
    private String country; // 국가/출신지 (필터링용)

    @Field(type = FieldType.Text, analyzer = "standard")
    private String agency; // 소속사/레이블

    // 상태 정보
    @Field(type = FieldType.Boolean)
    private Boolean isVerified; // 인증된 아티스트 여부

    @Field(type = FieldType.Boolean)
    private Boolean isActive; // 활동 상태

    // 통계 정보 (검색 정렬/필터링에 중요)
    @Field(type = FieldType.Long)
    private Long followerCount; // 팔로워 수

    @Field(type = FieldType.Long)
    private Long totalPlayCount; // 총 재생수

    @Field(type = FieldType.Long)
    private Long totalLikeCount; // 총 좋아요 수

    // 외부 연동 정보
    @Field(type = FieldType.Keyword)
    private String spotifyId; // Spotify 아티스트 ID

    @Field(type = FieldType.Keyword)
    private String appleMusicId; // Apple Music 아티스트 ID

    @Field(type = FieldType.Keyword)
    private String youtubeChannelId; // YouTube 채널 ID

    @Field(type = FieldType.Keyword)
    private String officialWebsite; // 공식 웹사이트

    @Field(type = FieldType.Keyword)
    private String instagramHandle; // 인스타그램 핸들

    @Field(type = FieldType.Keyword)
    private String twitterHandle; // 트위터 핸들

    // 타임스탬프 (날짜 범위 검색용)
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updatedAt;

    // 검색 최적화를 위한 추가 필드들

    // 전체 텍스트 검색용 (name + description + agency 조합)
    @Field(type = FieldType.Text, analyzer = "standard")
    private String searchText;

    // 인기도 점수 (followerCount, totalPlayCount, totalLikeCount 등을 조합한 점수)
    @Field(type = FieldType.Float)
    private Float popularityScore;

    // 검색 텍스트 생성 헬퍼 메소드
    public void generateSearchText() {
        StringBuilder sb = new StringBuilder();

        if (name != null) {
            sb.append(name).append(" ");
        }
        if (description != null) {
            sb.append(description).append(" ");
        }
        if (agency != null) {
            sb.append(agency).append(" ");
        }
        if (genre != null) {
            sb.append(genre).append(" ");
        }
        if (country != null) {
            sb.append(country).append(" ");
        }

        this.searchText = sb.toString().trim();
    }

    // 인기도 점수 계산 헬퍼 메소드
    public void calculatePopularityScore() {
        // 가중치를 적용한 인기도 점수 계산
        long followers = followerCount != null ? followerCount : 0;
        long plays = totalPlayCount != null ? totalPlayCount : 0;
        long likes = totalLikeCount != null ? totalLikeCount : 0;

        // 팔로워(50%), 재생수(30%), 좋아요(20%) 가중치
        this.popularityScore = (float) ((followers * 0.5) + (plays * 0.0003) + (likes * 0.2));
    }
}
