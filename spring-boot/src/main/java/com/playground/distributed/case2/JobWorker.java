package com.playground.distributed.case2;

import com.playground.distributed.config.AppConfig;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring @Scheduled 기반 백그라운드 작업 스트림 워커.
 * - 주기적으로 Redis Stream에서 미처리 메시지(ReadOffset.lastConsumed)를 폴링
 * - 작업을 PROCESSING -> 작업 수행 -> COMPLETED로 갱신하고 Stream ACK 전송
 */
@Component
public class JobWorker {

    private final JobQueueService queueService;
    private final StringRedisTemplate redisTemplate;
    private final String workerId;

    public JobWorker(
            JobQueueService queueService, StringRedisTemplate redisTemplate, AppConfig appConfig) {
        this.queueService = queueService;
        this.redisTemplate = redisTemplate;
        this.workerId = appConfig.getNodeId() + "-worker";
    }

    /**
     * 2초 간격으로 Redis Stream에서 작업을 읽어와 처리하는 스케줄러 메서드.
     */
    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay = 2000)
    public void processStream() {
        queueService.ensureConsumerGroup();
        try {
            // Consumer Group 단위로 마지막 소비 오프셋 이후의 새 메시지 읽기
            List<MapRecord<String, Object, Object>> messages =
                    redisTemplate
                            .opsForStream()
                            .read(
                                    Consumer.from(JobQueueService.CONSUMER_GROUP, workerId),
                                    StreamOffset.create(
                                            JobQueueService.STREAM_NAME,
                                            ReadOffset.lastConsumed()));

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : messages) {
                Map<Object, Object> value = record.getValue();
                String jobId = (String) value.get("job_id");
                String taskType = (String) value.get("task_type");

                if (jobId == null) {
                    redisTemplate
                            .opsForStream()
                            .acknowledge(
                                    JobQueueService.STREAM_NAME,
                                    JobQueueService.CONSUMER_GROUP,
                                    record.getId());
                    continue;
                }

                // 작업 진행 중 상태 갱신
                queueService.updateJobStatus(jobId, "PROCESSING", workerId, "");
                // 비즈니스 처리 시뮬레이션
                Thread.sleep(500);
                String resultStr = "Processed '" + taskType + "' successfully";
                queueService.updateJobStatus(jobId, "COMPLETED", workerId, resultStr);

                // 스트림 처리 완료 확인 응답(ACK) 전송
                redisTemplate
                        .opsForStream()
                        .acknowledge(
                                JobQueueService.STREAM_NAME,
                                JobQueueService.CONSUMER_GROUP,
                                record.getId());
            }
        } catch (Exception ignored) {
            // Redis 연결 실패 또는 스트림 미생성 상태 예외 무시
        }
    }
}
