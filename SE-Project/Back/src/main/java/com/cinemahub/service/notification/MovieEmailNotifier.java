package com.cinemahub.service.notification;

import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import com.cinemahub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Sends the real movie emails:
 * <ul>
 *   <li>movie APPROVED -> {@link NewMovieEmailNotification} to every active customer;</li>
 *   <li>movie PENDING -> {@link PendingApprovalEmailNotification} to every active System Administrator.</li>
 * </ul>
 * Runs only AFTER the approval/submission transaction has committed, and on a background thread
 * ({@code @Async}), so a slow or failing mail server can't delay or roll back that action.
 * If no mail server is configured (no spring.mail.host, e.g. a teammate's machine), it only logs.
 */
@Component
public class MovieEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MovieEmailNotifier.class);

    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String publicUrl;

    public MovieEmailNotifier(UserRepository userRepository,
                              ObjectProvider<JavaMailSender> mailSenderProvider,
                              @Value("${spring.mail.username:}") String fromAddress,
                              @Value("${cinemahub.public-url:http://localhost:8080}") String publicUrl) {
        this.userRepository = userRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Async
    @TransactionalEventListener
    public void onMovieApprovalEvent(MovieApprovalEvent event) {
        try {
            var mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null || fromAddress.isBlank()) {
                log.info("Email not configured (spring.mail.*) - skipping {} email for movie '{}'",
                        event.kind(), event.movieTitle());
                return;
            }
            if (event.kind() == MovieApprovalEvent.Kind.APPROVED) {
                emailCustomers(mailSender, event);
            } else {
                emailAdmins(mailSender, event);
            }
        } catch (RuntimeException ex) {
            // Belt and braces: EmailNotification already catches mail errors; this covers e.g. a DB hiccup.
            log.warn("Movie email for '{}' failed: {}", event.movieTitle(), ex.getMessage());
        }
    }

    private void emailCustomers(JavaMailSender mailSender, MovieApprovalEvent event) {
        List<User> customers = userRepository.findByRoleAndStatus(Role.CUSTOMER, UserStatus.ACTIVE);
        Notification email = NotificationFactory.createMovieEmail(NotificationType.NEW_MOVIE_EMAIL, mailSender,
                fromAddress, event.movieTitle(), publicUrl + "/movies/" + event.movieId());
        String message = event.movieTitle()
                + (event.genre() == null || event.genre().isBlank() ? "" : " (" + event.genre() + ")")
                + " has just been added at CinemaX Lanka. Check the showtimes and book your seats.";
        log.info("Movie '{}' approved - emailing {} customer(s)", event.movieTitle(), customers.size());
        customers.forEach(customer -> email.send(customer, message));
    }

    private void emailAdmins(JavaMailSender mailSender, MovieApprovalEvent event) {
        List<User> admins = userRepository.findByRoleAndStatus(Role.SYSTEM_ADMIN, UserStatus.ACTIVE);
        Notification email = NotificationFactory.createMovieEmail(NotificationType.PENDING_APPROVAL_EMAIL,
                mailSender, fromAddress, event.movieTitle(), publicUrl + "/admin/approvals");
        String message = event.submittedBy() + " submitted the movie \"" + event.movieTitle()
                + "\". It stays hidden from customers until you approve it in the approval queue.";
        log.info("Movie '{}' is PENDING - emailing {} admin(s)", event.movieTitle(), admins.size());
        admins.forEach(admin -> email.send(admin, message));
    }
}
