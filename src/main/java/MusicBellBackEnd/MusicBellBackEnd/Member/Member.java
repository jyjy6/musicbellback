package MusicBellBackEnd.MusicBellBackEnd.Member;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String username;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String displayName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String sex;
    private Integer age;

    private String phone;

    private String profileImage;
    private String country;
    private String mainAddress;
    private String subAddress;

    private LocalDateTime lastLogin;
    private Integer loginAttempts;
    private LocalDateTime loginSuspendedTime;

    private boolean termsAccepted;
    private boolean privacyAccepted;
    private boolean marketingAccepted = false;

    private boolean isSuperAdmin = false;
    private boolean isPremium = false;

    private LocalDateTime premiumExpiryDate;


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<String> roles = new HashSet<>(Set.of("ROLE_USER")); // 기본 역할


    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public void addRole(String role) {
        roles.add(role);
    }

    public void removeRole(String role) {
        roles.remove(role);
        if (roles.isEmpty()) {
            roles.add("ROLE_USER");
        }
    }

    public Set<String> getRoleSet() {
        return new HashSet<>(roles);
    }

    //기본 Role저장
    @PrePersist
    public void ensureDefaultRole() {
        if (roles == null || roles.isEmpty()) {
            System.out.println("[@PrePersist] roles가 null이라 기본값 ROLE_USER 세팅");
            roles = new HashSet<>();
            roles.add("ROLE_USER");
        }
    }


}
