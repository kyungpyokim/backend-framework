package com.playground.distributed.case2;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JobQueueService {

    public static final String STREAM_NAME = "stream:jobs";
    public static final String CONSUMER_GROUP = "group:workers";

    private final StringRedisTemplate redisTemplate;

    public JobQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_NAME, CONSUMER_GROUP);
        } catch (Exception e) {
            // Group already exists or stream created
        }
    }

    public String enqueueJob(String taskType, Map<String, Object> payload) {
        ensureConsumerGroup();
        String jobId = UUID.randomUUID().toString();

        Map<String, String> jobData = new HashMap<>();
        jobData.put("job_id", jobId);
        jobData.put("task_type", taskType);
        jobData.put("payload", payload != null ? payload.toString() : "{}");
        jobData.put("status", "PENDING");
        jobData.put("result", "");
        jobData.put("worker_id", "");
        jobData.put("created_at", String.valueOf(System.currentTimeMillis() / 1000));

        redisTemplate.opsForHash().putAll("job:" + jobId, jobData);

        Map<String, String> streamFields = Map.of(
            "job_id", jobId,
            "task_type", taskType
        );
        MapRecord<String, String, String> record = StreamRecords.string(streamFields).withStreamKey(STREAM_NAME);
        try {
            redisTemplate.opsForStream().add(record);
        } catch (Exception ignored) {}

        return jobId;
    }

    public Map<Object, Object> getJob(String jobId) {
        return redisTemplate.opsForHash().entries("job:" + jobId);
    }

    public void updateJobStatus(String jobId, String status, String workerId, String result) {
        Map<String, String> updates = new HashMap<>();
        updates.put("status", status);
        if (workerId != null) updates.put("worker_id", workerId);
        if (result != null) updates.put("result", result);
        redisTemplate.opsForHash().putAll("job:" + jobId, updates);
    }
}
