package com.project.Xclone_backend.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Cloudflare R2 speaks the S3 API; region must be "auto" and path-style addressing is used. */
@Configuration
@Conditional(R2ConfiguredCondition.class)
public class R2Config {

    private static final Region R2_REGION = Region.of("auto");

    @Bean(destroyMethod = "close")
    S3Client r2Client(R2Properties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentials(props))
                .serviceConfiguration(s3Config())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner r2Presigner(R2Properties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentials(props))
                .serviceConfiguration(s3Config())
                .build();
    }

    private static StaticCredentialsProvider credentials(R2Properties props) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }

    private static S3Configuration s3Config() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
