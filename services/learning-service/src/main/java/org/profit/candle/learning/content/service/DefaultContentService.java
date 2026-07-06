package org.profit.candle.learning.content.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.dto.ContentResult;
import org.profit.candle.learning.content.dto.CreateContentCommand;
import org.profit.candle.learning.content.dto.UpdateContentCommand;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.content.repository.ContentRepository;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
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
public class DefaultContentService implements ContentService {

    private final ContentRepository contentRepository;

    @Override
    public ContentResult getById(UUID id) {
        return ContentResult.from(loadContent(id));
    }

    @Override
    @Transactional
    public ContentResult getAndIncrementReadCount(UUID id) {
        Content content = loadContent(id);
        contentRepository.incrementReadCount(id);
        return ContentResult.from(content);
    }

    @Override
    @Transactional
    public ContentResult create(CreateContentCommand cmd) {
        Content content = Content.create(
                cmd.title(), cmd.description(), cmd.category(), cmd.level(),
                cmd.body(), cmd.durationMin(), cmd.xpReward(),
                cmd.keywords(), cmd.published());
        return ContentResult.from(contentRepository.save(content));
    }

    @Override
    @Transactional
    public ContentResult update(UpdateContentCommand cmd) {
        Content content = loadContent(cmd.contentId());
        content.update(cmd.title(), cmd.description(), cmd.category(), cmd.level(),
                cmd.body(), cmd.durationMin(), cmd.xpReward(),
                cmd.keywords(), cmd.published());
        return ContentResult.from(content);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        loadContent(id).softDelete();
    }

//    @Override
//    public Page<ContentResult> list(String category, ContentLevel level,
//                                    String sortBy, int page, int size) {
//        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
//        return contentRepository.findPublished(category, level, pageable)
//                .map(ContentResult::from);
//    }

//    @Override
//    public Page<ContentResult> search(String query, String category,
//                                      ContentLevel level, int page, int size) {
//        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        return contentRepository.searchByTitle(query, category, level, pageable)
//                .map(ContentResult::from);
//    }
    @Override
    public Page<ContentResult> list(String category, ContentLevel level,
                                    String sortBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
        return contentRepository.findAll(publishedSpec(category, level, null), pageable)
                .map(ContentResult::from);
    }

    @Override
    public Page<ContentResult> adminList(Boolean published, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.findAll(adminSpec(published), pageable)
                .map(ContentResult::from);
    }

    @Override
    public Page<ContentResult> search(String query, String category,
                                      ContentLevel level, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.findAll(publishedSpec(category, level, query), pageable)
                .map(ContentResult::from);
    }

    // Specification 메서드는 팀원 코드 그대로 수용
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
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("title")),
                        "%" + query.toLowerCase() + "%"));
            }
            return predicate;
        };
    }

    private Specification<Content> adminSpec(Boolean published) {
        return (root, cq, cb) -> published == null
                ? cb.conjunction()
                : cb.equal(root.get("published"), published);
    }

    @Override
    public long countAll() {
        return contentRepository.countByPublishedTrue();
    }

    @Override
    public long countByCategory(String category) {
        return contentRepository.countByCategoryAndPublishedTrue(category);
    }

    private Content loadContent(UUID id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new LearningException(LearningErrorCode.CONTENT_NOT_FOUND));
    }

    private Sort resolveSort(String sortBy) {
        return switch (sortBy) {
            case "POPULAR", "READ_COUNT" -> Sort.by(Sort.Direction.DESC, "readCount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}