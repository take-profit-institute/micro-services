package org.profit.candle.learning.content.service;

import org.profit.candle.learning.content.dto.ContentResult;
import org.profit.candle.learning.content.dto.CreateContentCommand;
import org.profit.candle.learning.content.dto.UpdateContentCommand;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.springframework.data.domain.Page;

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