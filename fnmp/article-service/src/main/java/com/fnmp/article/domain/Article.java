package com.fnmp.article.domain;

import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class Article extends BaseEntity {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private ArticleId id;

    @Column(nullable = false, length = 512)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ArticleStatus status = ArticleStatus.PUBLISHED;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ArticleCategory category;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ArticleTag> tags = new HashSet<>();

    @Column(name = "search_vector", columnDefinition = "TSVECTOR", insertable = false, updatable = false)
    private String searchVector;

    public UUID getUuid() {
        return id != null ? id.getId() : null;
    }

    public Instant getPublicationTimestamp() {
        return id != null ? id.getPublicationTimestamp() : null;
    }

    public void addTag(Tag tag) {
        ArticleTag articleTag = new ArticleTag(this, tag);
        tags.add(articleTag);
    }

    public void removeTag(Tag tag) {
        tags.removeIf(at -> at.getTag().equals(tag));
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = new ArticleId(UUID.randomUUID(), Instant.now());
        }
    }
}
