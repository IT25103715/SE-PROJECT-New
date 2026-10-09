package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/**
 * Base class for the notifications that send a REAL email through Spring's JavaMailSender
 * (Gmail SMTP, configured by the spring.mail.* properties). The older notifications only log a
 * line; these share the same {@link Notification} interface, so callers still just call send().
 *
 * send() never throws: a mail failure (no network, Gmail down, wrong app password) is logged and
 * swallowed, so it can never break the action that triggered the email.
 */
public abstract class EmailNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(EmailNotification.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    protected EmailNotification(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    protected abstract String subject();

    /** HTML body. {@code message} is the caller's short plain-text message (HTML-escaped by {@link #escape}). */
    protected abstract String htmlBody(User recipient, String message);

    @Override
    public void send(User recipient, String message) {
        String to = recipient.getEmail();
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(fromAddress, "CinemaX Lanka");
            helper.setTo(to);
            helper.setSubject(subject());
            helper.setText(htmlBody(recipient, message), true);
            mailSender.send(mime);
            log.info("[EMAIL to {}] Sent: {}", to, subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // Only the exception's own message is logged - never the SMTP credentials.
            log.warn("[EMAIL to {}] FAILED to send '{}': {}", to, subject(), ex.getMessage());
        }
    }

    protected static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }

    /** Shared dark-red CinemaX Lanka wrapper, so every email looks the same. */
    protected static String layout(String heading, String innerHtml) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;\
                border:1px solid #ddd;border-radius:8px;overflow:hidden">
                  <div style="background:#8b0000;color:#fff;padding:16px 24px;font-size:20px;font-weight:bold">
                    CinemaX Lanka
                  </div>
                  <div style="padding:24px;color:#222;line-height:1.5">
                    <h2 style="margin-top:0">%s</h2>
                    %s
                  </div>
                  <div style="background:#f4f4f4;color:#777;padding:12px 24px;font-size:12px">
                    You received this email because of your account at CinemaX Lanka.
                  </div>
                </div>
                """.formatted(heading, innerHtml);
    }

    protected static String button(String url, String label) {
        return "<p><a href=\"" + escape(url) + "\" style=\"display:inline-block;background:#c0392b;color:#fff;"
                + "padding:10px 20px;border-radius:6px;text-decoration:none;font-weight:bold\">"
                + escape(label) + "</a></p>"
                + "<p style=\"font-size:12px;color:#777\">Or open: " + escape(url) + "</p>";
    }
}
