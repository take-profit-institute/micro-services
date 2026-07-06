package org.profit.candle.batch.ranking.job;

import java.time.LocalDate;
import java.util.List;
import org.profit.candle.batch.portfolio.eod.job.PortfolioEodJobConfiguration;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

/** 같은 거래일의 Portfolio EOD 완료 여부를 Spring Batch 메타데이터로 확인한다. */
@Component
public class PortfolioEodCompletionGuard {

    private static final int PAGE_SIZE = 100;

    private final JobRepository jobRepository;

    public PortfolioEodCompletionGuard(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /** 수동 실행의 runId 유무와 관계없이 거래일이 같은 완료 실행을 찾는다. */
    public boolean completed(LocalDate businessDate) {
        for (int start = 0; ; start += PAGE_SIZE) {
            List<JobInstance> instances = jobRepository.getJobInstances(
                    PortfolioEodJobConfiguration.JOB_NAME,
                    start,
                    PAGE_SIZE
            );
            if (instances.isEmpty()) {
                return false;
            }
            if (instances.stream().anyMatch(instance -> completed(instance, businessDate))) {
                return true;
            }
            if (instances.size() < PAGE_SIZE) {
                return false;
            }
        }
    }

    /** 한 JobInstance의 실행 중 거래일과 완료 상태가 모두 일치하는지 확인한다. */
    private boolean completed(JobInstance instance, LocalDate businessDate) {
        return jobRepository.getJobExecutions(instance).stream()
                .anyMatch(execution -> completed(execution, businessDate));
    }

    /** EOD 실행의 businessDate와 최종 상태를 검사한다. */
    private boolean completed(JobExecution execution, LocalDate businessDate) {
        String executedDate = execution.getJobParameters().getString("businessDate");
        return businessDate.toString().equals(executedDate)
                && execution.getStatus() == BatchStatus.COMPLETED;
    }
}
