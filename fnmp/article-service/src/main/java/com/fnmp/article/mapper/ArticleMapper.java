package com.fnmp.article.mapper;

import com.fnmp.article.domain.Article;
import com.fnmp.article.domain.ArticleTag;
import com.fnmp.article.dto.ArticleResponse;
import com.fnmp.article.dto.ArticleSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(target = "id", source = "id.id")
    @Mapping(target = "publicationTimestamp", source = "id.publicationTimestamp")
    @Mapping(target = "source", source = "source.name")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagsToStrings")
    ArticleResponse toResponse(Article article);

    @Mapping(target = "id", source = "id.id")
    @Mapping(target = "publicationTimestamp", source = "id.publicationTimestamp")
    @Mapping(target = "source", source = "source.name")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagsToStrings")
    ArticleSummaryResponse toSummaryResponse(Article article);

    List<ArticleSummaryResponse> toSummaryResponses(List<Article> articles);

    @Named("tagsToStrings")
    default List<String> tagsToStrings(Set<ArticleTag> articleTags) {
        if (articleTags == null) {
            return Collections.emptyList();
        }
        return articleTags.stream()
                .map(at -> at.getTag().getName())
                .sorted()
                .toList();
    }
}