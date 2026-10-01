package com.project.Xclone_backend.media;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.project.Xclone_backend.common.ApiException;
import com.project.Xclone_backend.config.R2Properties;
import com.project.Xclone_backend.media.MediaDtos.UploadUrlResponse;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class MediaService {

    static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final R2Properties props;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectProvider<S3Presigner> s3Presigner;

    public MediaService(R2Properties props, ObjectProvider<S3Client> s3Client, ObjectProvider<S3Presigner> s3Presigner) {
        this.props = props;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public UploadUrlResponse createUploadUrl(Long userId, String contentType, long contentLength) {
        String type = contentType.strip().toLowerCase(Locale.ROOT);
        String ext = ALLOWED_TYPES.get(type);
        if (ext == null) {
            throw ApiException.badRequest("Unsupported image type; allowed: " + ALLOWED_TYPES.keySet());
        }
        if (contentLength > props.maxImageBytes()) {
            throw ApiException.badRequest("Image exceeds the " + props.maxImageBytes() + " byte limit");
        }

        String key = userPrefix(userId) + UUID.randomUUID() + "." + ext;
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(type)
                .contentLength(contentLength)
                .build();
        PresignedPutObjectRequest presigned = presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .putObjectRequest(put)
                .build());

        return new UploadUrlResponse(key, presigned.url().toString(), presigned.signedHeaders(),
                props.publicUrl(key), presigned.expiration());
    }

    /**
     * Ensures {@code key} lives under this user's prefix and that the upload actually reached the bucket. Prevents
     * attaching someone else's media or a key that was presigned but never uploaded.
     */
    public void verifyOwnedUpload(Long userId, String key) {
        if (key == null || !key.startsWith(userPrefix(userId)) || key.contains("..")) {
            throw ApiException.badRequest("Invalid media key: " + key);
        }
        HeadObjectResponse head;
        try {
            head = client().headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            throw ApiException.badRequest("Media not found, upload it first: " + key);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw ApiException.badRequest("Media not found, upload it first: " + key);
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Media storage error");
        }
        String type = head.contentType() == null ? "" : head.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.containsKey(type) || head.contentLength() > props.maxImageBytes()) {
            throw ApiException.badRequest("Uploaded media is not an allowed image: " + key);
        }
    }

    static String userPrefix(Long userId) {
        return "users/" + userId + "/";
    }


    private S3Client client() {
        S3Client client = s3Client.getIfAvailable();
        if (client == null) {
            throw notConfigured();
        }
        return client;
    }

    private S3Presigner presigner() {
        S3Presigner presigner = s3Presigner.getIfAvailable();
        if (presigner == null) {
            throw notConfigured();
        }
        return presigner;
    }

    private static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is not configured");
    }
}
