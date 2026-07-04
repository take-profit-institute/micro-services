package org.profit.candle.learning.content.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.content.repository.ContentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final ContentRepository contentRepository;

    public Content getById(UUID id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new LearningException(LearningErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 콘텐츠 상세 조회 + 조회수 증가.
     */
    @Transactional
    public Content getAndIncrementReadCount(UUID id) {
        Content content = getById(id);
        contentRepository.incrementReadCount(id);
        return content;
    }

    @Transactional
    public Content create(String title, String description, String category,
                          ContentLevel level, String body, short durationMin,
                          long xpReward, String[] keywords, boolean published) {
        Content content = Content.create(title, description, category, level,
                body, durationMin, xpReward, keywords, published);
        return contentRepository.save(content);
    }

    @Transactional
    public Content update(UUID id, String title, String description, String category,
                          ContentLevel level, String body, Short durationMin,
                          Long xpReward, String[] keywords, Boolean published) {
        Content content = getById(id);
        content.update(title, description, category, level, body,
                durationMin, xpReward, keywords, published);
        return content; // dirty checking
    }

    @Transactional
    public void softDelete(UUID id) {
        Content content = getById(id);
        content.softDelete();
    }

    public Page<Content> list(String category, ContentLevel level,
                              String sortBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
        return contentRepository.findAll(publishedSpec(category, level, null), pageable);
    }

    public Page<Content> search(String query, String category,
                                ContentLevel level, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.findAll(publishedSpec(category, level, query), pageable);
    }

    public long countAll() {
        return contentRepository.countByPublishedTrue();
    }

    public long countByCategory(String category) {
        return contentRepository.countByCategoryAndPublishedTrue(category);
    }

    private Sort resolveSort(String sortBy) {
        return switch (sortBy) {
            case "POPULAR", "READ_COUNT" -> Sort.by(Sort.Direction.DESC, "readCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt"); // LATEST
        };
    }

    private Specification<Content> publishedSpec(String category, ContentLevel level, String query) {
        return (root, cq, cb) -> {
            var predicate = cb.isTrue(root.get("published"));
            if (category != null && !category.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("category"), category));
            }
            if (level != null) {
                predicate = cb.and(predicate, cb.equal(root.get("level"), level));
            }
            if (query != null && !query.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("title")), "%" + query.toLowerCase() + "%"));
            }
            return predicate;
        };
    }
}
