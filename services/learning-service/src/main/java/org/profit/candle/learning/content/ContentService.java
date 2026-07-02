package org.profit.candle.learning.content;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.exception.ContentNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
                .orElseThrow(ContentNotFoundException::new);
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
        return contentRepository.findPublished(category, level, pageable);
    }

    public Page<Content> search(String query, String category,
                                ContentLevel level, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.searchByTitle(query, category, level, pageable);
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
}