package com.mcm.onboarding.domain.tag.entity;

import com.mcm.onboarding.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(unique = true, nullable = false, length = 50)
    private String tagCode;

    @Column(unique = true, nullable = false, length = 50)
    private String authCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagStatus status = TagStatus.UNREGISTERED;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Tag create(Product product, String tagCode, String authCode) {
        Tag tag = new Tag();
        tag.product = product;
        tag.tagCode = tagCode;
        tag.authCode = authCode;
        tag.status = TagStatus.UNREGISTERED;
        tag.createdAt = LocalDateTime.now();
        return tag;
    }

    public void markRegistered() {
        this.status = TagStatus.REGISTERED;
    }

    public void markUnregistered() {
        this.status = TagStatus.UNREGISTERED;
    }

    public boolean isRegistered() {
        return this.status == TagStatus.REGISTERED;
    }
}
