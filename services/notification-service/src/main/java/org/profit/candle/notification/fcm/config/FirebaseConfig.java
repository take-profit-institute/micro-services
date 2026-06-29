package org.profit.candle.notification.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
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
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        Resource resource = credentialResource();
        try (InputStream inputStream = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .build();

            return FirebaseApp.initializeApp(options);
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
