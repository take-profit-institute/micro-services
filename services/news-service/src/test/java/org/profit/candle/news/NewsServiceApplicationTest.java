package org.profit.candle.news;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.article.repository.ArticleJpaRepository;
import org.profit.candle.news.log.repository.CollectionLogJpaRepository;
import org.profit.candle.news.mapping.repository.ArticleStockMappingJpaRepository;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        }
)
class NewsServiceApplicationTest {
    @MockitoBean
    private ArticleJpaRepository articleJpaRepository;

    @MockitoBean
    private ArticleStockMappingJpaRepository articleStockMappingJpaRepository;

    @MockitoBean
    private CollectionTargetJpaRepository collectionTargetJpaRepository;

    @MockitoBean
    private CollectionLogJpaRepository collectionLogJpaRepository;

    @Test
    void shouldLoadApplicationContext() {
    }
}
