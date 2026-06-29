package org.profit.candle.batch.support.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BatchLoggingListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info(
                "[Batch Start] jobName={}, jobInstanceId={}, jobParameters={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobInstanceId(),
                jobExecution.getJobParameters()
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.warn(
                    "[Batch Failed] jobName={}, jobInstanceId={}, status={}, exitStatus={}",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getJobInstanceId(),
                    jobExecution.getStatus(),
                    jobExecution.getExitStatus()
            );
            return;
        }

        log.info(
                "[Batch End] jobName={}, jobInstanceId={}, status={}, exitStatus={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobInstanceId(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus()
        );
    }
}