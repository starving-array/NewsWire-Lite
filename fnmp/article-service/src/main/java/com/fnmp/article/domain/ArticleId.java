package com.fnmp.article.domain;

import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import com.fnmp.common.domain.ReliabilityTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleId implements java.io.Serializable {

    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "publication_timestamp", nullable = false)
    private Instant publicationTimestamp;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArticleId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(publicationTimestamp, that.publicationTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, publicationTimestamp);
    }
}