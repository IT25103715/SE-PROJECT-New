package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real email to every customer when a movie is approved and goes live
 * ("New at CinemaX Lanka: <title>"), with a link back to the movie's detail page.
 */
public class NewMovieEmailNotification extends EmailNotification {

    private final String movieTitle;
    private final String movieUrl;

    public NewMovieEmailNotification(JavaMailSender mailSender, String fromAddress,
                                     String movieTitle, String movieUrl) {
        super(mailSender, fromAddress);
        this.movieTitle = movieTitle;
        this.movieUrl = movieUrl;
    }

    @Override
    protected String subject() {
        return "New at CinemaX Lanka: " + movieTitle;
    }

    @Override
    protected String htmlBody(User recipient, String message) {
        return layout("New at CinemaX Lanka: " + escape(movieTitle),
                "<p>Hi " + escape(recipient.getName()) + ",</p>"
                        + "<p>" + escape(message) + "</p>"
                        + button(movieUrl, "View " + movieTitle)
                        + "<p>See you at the movies!</p>");
    }
}
