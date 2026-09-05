package com.playground.distributed.case2;

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

@RestController
@RequestMapping("/case2")
@SuppressWarnings({"null", "unchecked"})
public class Case2Controller {

    private final JobQueueService queueService;
    private final AppConfig appConfig;

    public Case2Controller(JobQueueService queueService, AppConfig appConfig) {
        this.queueService = queueService;
        this.appConfig = appConfig;
    }

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> submitJob(
            @RequestBody(required = false) Map<String, Object> body) {
        String taskType =
                body != null && body.containsKey("task_type")
                        ? (String) body.get("task_type")
                        : "heavy_computation";
        Map<String, Object> payload =
                body != null && body.containsKey("payload")
                        ? (Map<String, Object>) body.get("payload")
                        : Map.of("duration_sec", 1);

        String jobId = queueService.enqueueJob(taskType, payload);

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

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<Object, Object>> getJobStatus(@PathVariable String jobId) {
        Map<Object, Object> job = queueService.getJob(jobId);
        if (job == null || job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}
