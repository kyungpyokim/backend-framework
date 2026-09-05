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

@Component
@SuppressWarnings({"null", "unchecked"})
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

    @Scheduled(fixedDelay = 2000)
    public void processStream() {
        queueService.ensureConsumerGroup();
        try {
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

                queueService.updateJobStatus(jobId, "PROCESSING", workerId, "");
                // Simulate processing
                Thread.sleep(500);
                String resultStr = "Processed '" + taskType + "' successfully";
                queueService.updateJobStatus(jobId, "COMPLETED", workerId, resultStr);

                redisTemplate
                        .opsForStream()
                        .acknowledge(
                                JobQueueService.STREAM_NAME,
                                JobQueueService.CONSUMER_GROUP,
                                record.getId());
            }
        } catch (Exception ignored) {
            // Redis connection or stream not ready yet
        }
    }
}
