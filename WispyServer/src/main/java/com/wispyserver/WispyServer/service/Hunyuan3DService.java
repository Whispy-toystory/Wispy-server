package com.wispyserver.WispyServer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wispyserver.WispyServer.entity.GlbGenerationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class Hunyuan3DService {

    @Value("${hunyuan3d.api.base-url:http://your-hunyuan-server:8000}")
    private String hunyuanBaseUrl;

    @Value("${hunyuan3d.api.key:your-api-key}")
    private String hunyuanApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String submitGlbGenerationTask(
            Map<String, MultipartFile> images,
            GlbGenerationJob job) throws IOException {

        log.info("Submitting GLB generation task to Hunyuan3D for job: {}", job.getJobId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + hunyuanApiKey);
        headers.set("X-Job-ID", job.getJobId().toString());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        for (Map.Entry<String, MultipartFile> entry : images.entrySet()) {
            MultipartFile file = entry.getValue();

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return entry.getKey() + "_" + file.getOriginalFilename();
                }

                @Override
                public String getDescription() {
                    return "Image for " + entry.getKey() + " view";
                }
            };

            body.add(entry.getKey(), resource);
            log.debug("Added {} image: {} bytes", entry.getKey(), file.getSize());
        }

        body.add("output_format", "glb");
        body.add("quality", "high");
        body.add("texture_resolution", "1024");
        body.add("mesh_quality", "medium");
        body.add("callback_url", getCallbackUrl(job.getJobId()));

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        try {
            log.info("Sending {} images directly to Hunyuan3D model...", images.size());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    hunyuanBaseUrl + "/api/v1/generate-avatar",
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.ACCEPTED) {

                JsonNode responseBody = objectMapper.readTree(response.getBody());
                String taskId = responseBody.get("task_id").asText();

                log.info("Hunyuan3D task submitted successfully. Task ID: {}, Job ID: {}",
                        taskId, job.getJobId());
                return taskId;

            } else {
                throw new RuntimeException("Failed to submit task to Hunyuan3D: " +
                        response.getStatusCode() + " - " + response.getBody());
            }

        } catch (Exception e) {
            log.error("Error submitting images directly to Hunyuan3D for job: {}",
                    job.getJobId(), e);
            throw new RuntimeException("Failed to submit GLB generation task: " + e.getMessage(), e);
        }
    }

    public TaskStatus checkTaskStatus(String taskId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + hunyuanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    hunyuanBaseUrl + "/api/v1/task/" + taskId + "/status",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());

                String status = responseBody.get("status").asText();
                int progress = responseBody.has("progress") ? responseBody.get("progress").asInt() : 0;
                String resultUrl = responseBody.has("result_url") ?
                        responseBody.get("result_url").asText() : null;
                String errorMessage = responseBody.has("error") ?
                        responseBody.get("error").asText() : null;

                log.debug("Task {} status: {} ({}%)", taskId, status, progress);

                return TaskStatus.builder()
                        .status(status)
                        .progress(progress)
                        .resultUrl(resultUrl)
                        .errorMessage(errorMessage)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error checking task status for taskId: {}", taskId, e);
        }

        return TaskStatus.builder()
                .status("error")
                .errorMessage("Failed to check task status")
                .build();
    }

    public byte[] downloadGlbFile(String resultUrl) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + hunyuanApiKey);
            headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Downloading generated GLB file from: {}", resultUrl);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    resultUrl,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                byte[] glbData = response.getBody();
                log.info("GLB file downloaded successfully. Size: {} bytes",
                        glbData != null ? glbData.length : 0);
                return glbData;
            } else {
                throw new IOException("Failed to download GLB file: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error downloading GLB file from: {}", resultUrl, e);
            throw new IOException("Failed to download GLB file: " + e.getMessage(), e);
        }
    }

    private String getCallbackUrl(UUID jobId) {
        return String.format("https://your-domain.com/api/internal/hunyuan-callback/%s", jobId);
    }

    @lombok.Data
    @lombok.Builder
    public static class TaskStatus {
        private String status;
        private Integer progress;
        private String resultUrl;
        private String errorMessage;
    }
}