package org.profit.candle.notification.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.exception.NotificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;
    private final String credentialPath;

    public FirebaseConfig(
            ResourceLoader resourceLoader,
            @Value("${firebase.credentials.path:${FIREBASE_CREDENTIALS_PATH:classpath:firebase/firebase-adminsdk.json}}")
            String credentialPath
    ) {
        this.resourceLoader = resourceLoader;
        this.credentialPath = credentialPath;
    }

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        Resource resource = credentialResource();
        if (!resource.exists()) {
            throw new NotificationException(NotificationErrorCode.FIREBASE_CREDENTIAL_INVALID);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .build();

            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            throw new NotificationException(
                    NotificationErrorCode.FIREBASE_CREDENTIAL_INVALID,
                    e
            );
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private Resource credentialResource() {
        if (credentialPath.startsWith("classpath:") || credentialPath.startsWith("file:")) {
            return resourceLoader.getResource(credentialPath);
        }

        return new FileSystemResource(credentialPath);
    }
}
