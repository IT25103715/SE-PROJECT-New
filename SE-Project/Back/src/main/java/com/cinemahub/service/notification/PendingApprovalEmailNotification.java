package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real email to each System Administrator when a Manager submits a movie that now waits
 * (approvalStatus = PENDING) in the approval queue, with a link to that queue.
 */
public class PendingApprovalEmailNotification extends EmailNotification {

    private final String movieTitle;
    private final String approvalQueueUrl;

    public PendingApprovalEmailNotification(JavaMailSender mailSender, String fromAddress,
                                            String movieTitle, String approvalQueueUrl) {
        super(mailSender, fromAddress);
        this.movieTitle = movieTitle;
        this.approvalQueueUrl = approvalQueueUrl;
    }

    @Override
    protected String subject() {
        return "Approval needed: " + movieTitle;
    }

    @Override
    protected String htmlBody(User recipient, String message) {
        return layout("A movie is waiting for your approval",
                "<p>Hi " + escape(recipient.getName()) + ",</p>"
                        + "<p>" + escape(message) + "</p>"
                        + button(approvalQueueUrl, "Open the approval queue"));
    }
}
