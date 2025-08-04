package MusicBellBackEnd.MusicBellBackEnd.Auth.JWT;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

// GEMINI: ImgBell은 리소스 서버이므로, JWT 검증 기능만 남기고 생성 관련 코드는 모두 제거합니다.
// GEMINI: AuthBell에서 개인키로 서명한 토큰을 검증하기 위해 공개키를 사용하도록 수정합니다.
@Component
public class JWTUtil {

    private final PublicKey publicKey;

    public JWTUtil(@Value("${jwt.public-key}") Resource publicKeyResource) throws Exception {
        // GEMINI: Public Key 로드
        byte[] publicKeyBytes = publicKeyResource.getInputStream().readAllBytes();
        String publicKeyPEM = new String(publicKeyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("\r\n|\r|\n", "")
                .replace("-----END PUBLIC KEY-----", "");
        byte[] decodedPublicKey = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedPublicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    // GEMINI: JWT 토큰에서 클레임(Claims)을 추출하는 기능을 수행 (공개키로 검증)
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey) // 공개키로 검증
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // GEMINI: 이 메서드는 JWT 토큰이 만료되었는지 여부를 확인하는 기능을 수행
    public boolean isTokenExpired(String token) {
        try {
            final Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            System.out.println("토큰이 만료되었습니다: " + e.getMessage());
            return true;
        } catch (Exception e) {
            System.out.println("토큰 검증 중 오류 발생: " + e.getMessage());
            return true; // 오류 났으면 만료된 걸로 간주
        }
    }

    // GEMINI: 이 메서드는 JWT 토큰에서 사용자 이름을 추출하는 기능을 수행
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }
}