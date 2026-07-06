package org.profit.candle.market.session;

import org.profit.candle.market.entity.MarketHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, LocalDate> {
}
