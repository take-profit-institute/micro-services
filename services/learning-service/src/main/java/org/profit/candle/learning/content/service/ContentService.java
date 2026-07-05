package org.profit.candle.learning.content.service;

import org.profit.candle.learning.content.dto.ContentResult;
import org.profit.candle.learning.content.dto.CreateContentCommand;
import org.profit.candle.learning.content.dto.UpdateContentCommand;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ContentService {

    ContentResult getById(UUID id);

    ContentResult getAndIncrementReadCount(UUID id);

    ContentResult create(CreateContentCommand command);

    ContentResult update(UpdateContentCommand command);

    void softDelete(UUID id);

    Page<ContentResult> list(String category, ContentLevel level, String sortBy, int page, int size);

    Page<ContentResult> search(String query, String category, ContentLevel level, int page, int size);

    long countAll();

    long countByCategory(String category);
}
    public Page<Content> list(String category, ContentLevel level,
                              String sortBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
        return contentRepository.findAll(publishedSpec(category, level, null), pageable);
    }

    public Page<Content> adminList(Boolean published, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.findAll(adminSpec(published), pageable);
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

    private Specification<Content> adminSpec(Boolean published) {
        return (root, cq, cb) -> published == null
                ? cb.conjunction()
                : cb.equal(root.get("published"), published);
    }
}
