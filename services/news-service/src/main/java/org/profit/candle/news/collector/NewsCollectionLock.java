package org.profit.candle.news.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Slf4j
@Component
class NewsCollectionLock {
    private static final int LOCK_NAMESPACE = 1_850_001;
    private static final int LOCK_KEY = 1;

    private final DataSource dataSource;

    NewsCollectionLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    Optional<Handle> tryLock() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (!tryAcquire(connection)) {
                connection.close();
                return Optional.empty();
            }
            return Optional.of(new Handle(connection));
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("Failed to acquire news collection lock", e);
        }
    }

    private boolean tryAcquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_try_advisory_lock(?, ?)"
        )) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?, ?)"
        )) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, LOCK_KEY);
            statement.execute();
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Failed to close news collection lock connection", e);
        }
    }

    static final class Handle implements AutoCloseable {
        private final Connection connection;

        Handle(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            if (connection == null) {
                return;
            }
            try {
                unlock(connection);
            } catch (SQLException e) {
                log.warn("Failed to release news collection lock", e);
            } finally {
                closeQuietly(connection);
            }
        }
    }
}
