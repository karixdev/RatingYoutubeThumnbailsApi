package com.github.karixdev.youtubethumbnailranking.auth;

import com.github.karixdev.youtubethumbnailranking.emailverification.EmailVerificationToken;
import com.github.karixdev.youtubethumbnailranking.emailverification.EmailVerificationTokenRepository;
import com.github.karixdev.youtubethumbnailranking.user.User;
import com.github.karixdev.youtubethumbnailranking.user.UserRepository;
import com.github.karixdev.youtubethumbnailranking.user.UserRole;
import com.github.karixdev.youtubethumbnailranking.user.UserService;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.mail.internet.MimeMessage;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthControllerIT {
    @Autowired
    WebTestClient webClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    @Autowired
    UserService userService;

    @RegisterExtension
    static GreenMailExtension greenMail =
            new GreenMailExtension(ServerSetupTest.SMTP)
                    .withConfiguration(GreenMailConfiguration.aConfig()
                            .withUser("greenmail-user", "greenmail-password"))
                    .withPerMethodLifecycle(false);

    @AfterEach
    void tearDown() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldNotRegisterUserWhenProvidedNotAvailableEmail() {
        userService.createUser(
                "email@email.com",
                "username",
                "password",
                UserRole.ROLE_USER,
                Boolean.FALSE
        );

        String payload = """
                {
                    "email": "email@email.com",
                    "username": "not-taken-username",
                    "password": "password"
                }
                """;

        webClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        assertThat(userRepository.findAll().size())
                .isEqualTo(1);
    }

    @Test
    void shouldNotRegisterUserWhenProvidedNotAvailableUsername() {
        userService.createUser(
                "email@email.com",
                "username",
                "password",
                UserRole.ROLE_USER,
                Boolean.FALSE
        );

        String payload = """
                {
                    "email": "not-taken-email@email.com",
                    "username": "username",
                    "password": "password"
                }
                """;

        webClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        assertThat(userRepository.findAll().size())
                .isEqualTo(1);
        assertThat(tokenRepository.findAll())
                .isEmpty();
    }

    @Test
    void shouldRegisterUserAndCreateEmailVerificationToken() {
        String payload = """
                {
                    "email": "email@email.com",
                    "username": "username",
                    "password": "password"
                }
                """;

        webClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("success");

        assertThat(userRepository.findAll()).isNotEmpty();

        User user = userRepository.findAll().get(0);

        assertThat(user.getUserRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getEmail()).isEqualTo("email@email.com");
        assertThat(user.getUsername()).isEqualTo("username");
        assertThat(user.getIsEnabled()).isEqualTo(Boolean.FALSE);

        assertThat(tokenRepository.findAll().size())
                .isEqualTo(1);

        EmailVerificationToken token = tokenRepository.findAll().get(0);

        assertThat(token.getUser()).isEqualTo(user);

        await().atMost(2, SECONDS).untilAsserted(() -> {
            MimeMessage[] receivedMessages = greenMail.getReceivedMessages();

            assertThat(receivedMessages).hasSize(1);

            MimeMessage receivedMessage = receivedMessages[0];

            assertThat(receivedMessage.getSubject()).isEqualTo("Verify your email");
            assertThat(receivedMessage.getFrom()).hasSize(1);

            String from = receivedMessage.getFrom()[0].toString();
            assertThat(from).isEqualTo("test@youtube-thumbnail-ranking.com");

            assertThat(receivedMessage.getAllRecipients()).hasSize(1);

            String recipient = receivedMessage.getAllRecipients()[0].toString();
            assertThat(recipient).isEqualTo("email@email.com");
        });
    }

}