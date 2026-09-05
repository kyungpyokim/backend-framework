package com.playground.distributed.case2;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Stream 및 Hash 기반 분산 작업 큐(Job Queue) 서비스.
 * - 작업을 Stream(stream:jobs)에 추가하고 상세 상태를 Hash(job:{id})에 영속화합니다.
 */
@Service
public class JobQueueService {

    public static final String STREAM_NAME = "stream:jobs";
    public static final String CONSUMER_GROUP = "group:workers";

    private final StringRedisTemplate redisTemplate;

    public JobQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Stream에 Consumer Group이 존재하는지 확인하고 없으면 생성 */
    public void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_NAME, CONSUMER_GROUP);
        } catch (Exception e) {
            // Group already exists or stream created
        }
    }

    /**
     * 신규 비동기 작업을 큐에 인큐합니다.
     * 1. Hash(job:{job_id})에 메타데이터 저장 (PENDING 상태)
     * 2. Stream(stream:jobs)에 메시지 레코드 추가
     *
     * @param taskType 작업 유형
     * @param payload 작업 파라미터 맵
     * @return 생성된 작업 고유 ID (UUID)
     */
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

        Map<String, String> streamFields =
                Map.of(
                        "job_id", jobId,
                        "task_type", taskType);
        MapRecord<String, String, String> record =
                StreamRecords.string(streamFields).withStreamKey(STREAM_NAME);
        try {
            redisTemplate.opsForStream().add(record);
        } catch (Exception ignored) {
        }

        return jobId;
    }

    /** 작업 ID로 작업 상태 및 결과 정보 조회 */
    public Map<Object, Object> getJob(String jobId) {
        return redisTemplate.opsForHash().entries("job:" + jobId);
    }

    /** 워커 처리 단계에 따른 작업 상태 갱신 (PROCESSING, COMPLETED, FAILED) */
    public void updateJobStatus(String jobId, String status, String workerId, String result) {
        Map<String, String> updates = new HashMap<>();
        updates.put("status", status);
        if (workerId != null) updates.put("worker_id", workerId);
        if (result != null) updates.put("result", result);
        redisTemplate.opsForHash().putAll("job:" + jobId, updates);
    }
}
