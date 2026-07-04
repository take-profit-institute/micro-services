package org.profit.candle.learning.content.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContentLevelConverter implements AttributeConverter<ContentLevel, String> {

    @Override
    public String convertToDatabaseColumn(ContentLevel attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public ContentLevel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ContentLevel.fromDbValue(dbData);
    }
}