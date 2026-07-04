package org.profit.candle.market.session;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.entity.MarketHoliday;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DB {@code market_holidays} 기반 거래일 캘린더.
 *
 * 휴장일은 거의 안 바뀌므로 메모리에 캐시하고 하루 한 번 새로고침한다(batch 가 채운 값 반영).
 * 세션 판정 경로(MarketSession)가 매 틱 DB 를 때리지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbTradingCalendar implements TradingCalendar {

    private final MarketHolidayRepository repository;
    private volatile Set<LocalDate> holidays = Set.of();

    @Override
    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    @PostConstruct
    @Scheduled(cron = "${market.calendar.refresh-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        holidays = repository.findAll().stream()
                .map(MarketHoliday::holidayDate)
                .collect(Collectors.toUnmodifiableSet());
        log.info("거래 캘린더 새로고침: 휴장일 {}건", holidays.size());
    }
}
