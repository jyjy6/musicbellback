package MusicBellBackEnd.MusicBellBackEnd.Auth;

import MusicBellBackEnd.MusicBellBackEnd.Member.Member;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class CustomUserDetails implements UserDetails, OAuth2User {

    // Member 엔티티 반환 메소드 (추가 정보 접근용)
    @Getter
    private final Member member;
    private final Collection<SimpleGrantedAuthority> authorities;

    public CustomUserDetails(Member member) {
        this.member = member;
        this.authorities = convertRolesToAuthorities(member.getRoleSet());
    }

    private Map<String, Object> attributes;
    public CustomUserDetails(Member member, Collection<SimpleGrantedAuthority> authorities, Map<String, Object> attributes) {
        this.member = member;
        this.authorities = authorities;
        this.attributes = attributes;
    }

    // 역할 문자열을 SimpleGrantedAuthority 객체로 변환
    private Collection<SimpleGrantedAuthority> convertRolesToAuthorities(Collection<String> roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return member.getPassword();
    }

    @Override
    public String getUsername() {
        return member.getUsername();
    }



    // 계정 만료 여부: false = 만료됨
    @Override
    public boolean isAccountNonExpired() {
        return true; // 계정 만료 기능이 없으므로 항상 true 반환
    }

    // 계정 잠금 여부: false = 잠김
    @Override
    public boolean isAccountNonLocked() {
        // 로그인 시도 제한 기능을 사용하는 경우 확인
        if (member.getLoginSuspendedTime() != null) {
            return LocalDateTime.now().isAfter(member.getLoginSuspendedTime());
        }
        return true;
    }

    // 자격 증명 만료 여부: false = 만료됨
    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 보안 자격 증명 만료 기능이 없으므로 항상 true 반환
    }

    // 계정 활성화 여부: false = 비활성화
    @Override
    public boolean isEnabled() {
        // 이메일 인증이 필요한 경우 확인
        return true; // 현재는 모든 계정 활성화
    }

    // 사용자 ID 반환 메소드
    public Long getId() {
        return member.getId();
    }

    // 프리미엄 상태 확인 메소드
    public boolean isPremium() {
        if (!member.getRoleSet().contains("ROLE_PREMIUM")) {
            return false;
        }

        // 프리미엄 만료일 확인
        LocalDateTime expiryDate = member.getPremiumExpiryDate();
        return expiryDate != null && expiryDate.isAfter(LocalDateTime.now());
    }

    // 디스플레이 이름 반환 메소드
    public String getDisplayName() {
        return member.getDisplayName();
    }

    // 이메일 반환 메소드
    public String getEmail() {
        return member.getEmail();
    }

    @Override
    public String getName() {
        return member.getUsername();
    }
}