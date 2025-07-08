package com.wispyserver.WispyServer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    public String uploadGlbFile(UUID characterId, MultipartFile file) throws IOException {
        log.info("Uploading GLB file for character: {}, file size: {} bytes",
                characterId, file.getSize());

        S3Client s3Client = createS3Client();

        try {
            String key = generateS3Key(characterId, file.getOriginalFilename());

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("model/gltf-binary")
                    .contentLength(file.getSize())
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            String fileUrl = generateFileUrl(key);

            log.info("GLB file uploaded successfully. URL: {}, ETag: {}",
                    fileUrl, response.eTag());

            return fileUrl;

        } finally {
            s3Client.close();
        }
    }

    private S3Client createS3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    private String generateS3Key(UUID characterId, String originalFilename) {
        long timestamp = System.currentTimeMillis();
        String fileExtension = getFileExtension(originalFilename);
        return String.format("characters/%s/%d_%s%s",
                characterId.toString(), timestamp, characterId.toString(), fileExtension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".glb";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    private String generateFileUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    public void validateGlbFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".glb")) {
            throw new IllegalArgumentException("Only GLB files are allowed");
        }
    }

    public String uploadTempImage(UUID characterId, String imageType, MultipartFile file) throws IOException {
        String fileName = String.format("temp/%s/%s_%s", characterId, imageType, file.getOriginalFilename());
        return uploadFile(fileName, file);
    }

    public String uploadFile(String key, MultipartFile file) throws IOException {
        log.info("Uploading file to S3: {}, file size: {} bytes", key, file.getSize());

        S3Client s3Client = createS3Client();

        try {
            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            String fileUrl = generateFileUrl(key);

            log.info("File uploaded successfully. URL: {}, ETag: {}", fileUrl, response.eTag());

            return fileUrl;

        } finally {
            s3Client.close();
        }
    }
}
