package com.fnmp.article.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "article_tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleTag {

    @EmbeddedId
    private ArticleTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("article")
    @JoinColumns({
        @JoinColumn(name = "article_id", referencedColumnName = "id"),
        @JoinColumn(name = "article_pub_timestamp", referencedColumnName = "publication_timestamp")
    })
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id")
    private Tag tag;

    public ArticleTag(Article article, Tag tag) {
        this.article = article;
        this.tag = tag;
        this.id = new ArticleTagId(article.getId(), tag.getId());
    }
}
