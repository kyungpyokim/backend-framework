package com.playground.distributed.case2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 2: 비동기 작업 큐 및 워커 REST 컨트롤러.
 * - 신규 비동기 작업 제출 (/case2/jobs)
 * - 작업 진행 상태 및 결과 조회 (/case2/jobs/{jobId})
 */
@RestController
@RequestMapping("/case2")
public class Case2Controller {

    /** 작업 제출 요청 불변 Record DTO (Null Object 패턴 적용) */
    public record JobSubmitRequest(
            @JsonProperty("task_type") String taskType,
            @JsonProperty("payload") Map<String, Object> payload) {
        public static final JobSubmitRequest DEFAULT =
                new JobSubmitRequest("heavy_computation", Map.of("duration_sec", 1));

        public JobSubmitRequest {
            taskType = (taskType != null && !taskType.isBlank()) ? taskType : "heavy_computation";
            payload = payload != null ? payload : Map.of("duration_sec", 1);
        }

        public static JobSubmitRequest ofNullable(JobSubmitRequest req) {
            return req != null ? req : DEFAULT;
        }
    }

    private final JobQueueService queueService;
    private final AppConfig appConfig;

    public Case2Controller(JobQueueService queueService, AppConfig appConfig) {
        this.queueService = queueService;
        this.appConfig = appConfig;
    }

    /**
     * [작업 인큐] 새로운 비동기 백그라운드 작업을 등록하고 202 Accepted와 작업 ID를 반환합니다.
     */
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> submitJob(
            @RequestBody(required = false) JobSubmitRequest req) {
        var request = JobSubmitRequest.ofNullable(req);
        String jobId = queueService.enqueueJob(request.taskType(), request.payload());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        Map.of(
                                "node_id",
                                appConfig.getNodeId(),
                                "job_id",
                                jobId,
                                "status",
                                "PENDING",
                                "message",
                                "Job accepted and enqueued. Poll GET /case2/jobs/"
                                        + jobId
                                        + " for status."));
    }

    /**
     * [작업 상태 조회] 작업 ID로 상태(PENDING, PROCESSING, COMPLETED, FAILED) 및 결과를 조회합니다.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<Object, Object>> getJobStatus(@PathVariable String jobId) {
        Map<Object, Object> job = queueService.getJob(jobId);
        if (job == null || job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}
