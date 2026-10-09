package com.cinemahub.controller;

import com.cinemahub.dto.FeedbackForm;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.Feedback;
import com.cinemahub.model.FeedbackType;
import com.cinemahub.model.Movie;
import com.cinemahub.model.User;
import com.cinemahub.service.BookingService;
import com.cinemahub.service.FeedbackService;
import com.cinemahub.service.MovieService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

/** Function 5, customer-facing side: submit and browse your own feedback/complaints. */
@Controller
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserService userService;
    private final BookingService bookingService;
    private final MovieService movieService;

    public FeedbackController(FeedbackService feedbackService, UserService userService,
                               BookingService bookingService, MovieService movieService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
        this.bookingService = bookingService;
        this.movieService = movieService;
    }

    /**
     * The feedback page. With ?bookingId=… (the "Rate this movie" link on a booking) the form opens
     * as a review of that booking's movie, so the customer only has to pick stars and write.
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long bookingId, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        FeedbackForm form = new FeedbackForm();
        if (bookingId != null) {
            Booking booking = ownedBookingOrNull(bookingId, user);
            if (booking != null) {
                form.setType(FeedbackType.REVIEW);
                form.setBookingId(booking.getId());
                form.setMovieId(booking.getShowtime().getMovie().getId());
            }
        }
        model.addAttribute("feedbackForm", form);
        addPageData(model, user);
        return "feedback/list";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute FeedbackForm feedbackForm, BindingResult bindingResult,
                          Authentication authentication, Model model) {
        User user = currentUser(authentication);
        if (!bindingResult.hasErrors()) {
            try {
                feedbackService.submit(user, feedbackForm);
                return "redirect:/feedback";
            } catch (IllegalArgumentException ex) {
                // e.g. a rated review with no movie chosen - show it under the Movie field
                bindingResult.rejectValue("movieId", "feedback.movie", ex.getMessage());
            }
        }
        addPageData(model, user);
        return "feedback/list";
    }

    /** Pre-fills the same list.html form in "edit" mode - locked out (redirects back) once the feedback is Resolved. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        Feedback feedback = ownedFeedback(id, user);
        if (feedback.isResolved()) {
            return "redirect:/feedback";
        }

        FeedbackForm form = new FeedbackForm();
        form.setType(feedback.getType());
        form.setMessage(feedback.getMessage());
        form.setRating(feedback.getRating());
        form.setBookingId(feedback.getBooking() != null ? feedback.getBooking().getId() : null);
        form.setMovieId(feedback.getMovie() != null ? feedback.getMovie().getId() : null);

        model.addAttribute("feedbackForm", form);
        model.addAttribute("editingId", id);
        addPageData(model, user);
        return "feedback/list";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute FeedbackForm feedbackForm, BindingResult bindingResult,
                        Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (!bindingResult.hasErrors()) {
            try {
                feedbackService.update(id, user.getId(), feedbackForm);
                return "redirect:/feedback";
            } catch (IllegalStateException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/feedback";
            } catch (IllegalArgumentException ex) {
                bindingResult.rejectValue("movieId", "feedback.movie", ex.getMessage());
            }
        }
        model.addAttribute("editingId", id);
        addPageData(model, user);
        return "feedback/list";
    }

    /** Everything the feedback page shows besides the form: history, the customer's bookings, and the movies to review. */
    private void addPageData(Model model, User user) {
        model.addAttribute("feedbackList", feedbackService.findByUser(user.getId()));
        model.addAttribute("bookings", bookingService.findByUser(user.getId()));
        List<Movie> movies = movieService.search(null, null).stream()
                .sorted(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();
        model.addAttribute("movies", movies);
    }

    /** The logged-in customer's own booking, or null for someone else's / a missing one (never trust the id in the URL alone). */
    private Booking ownedBookingOrNull(Long bookingId, User user) {
        try {
            Booking booking = bookingService.findById(bookingId);
            return booking.getUser().getId().equals(user.getId()) ? booking : null;
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }

    /** Hard delete - this is the customer withdrawing their own content, not CRE's separate admin-side delete. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            feedbackService.deleteOwn(id, user.getId());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/feedback";
    }

    /** Loads a feedback row and confirms it belongs to the logged-in user, 404-ing otherwise (never trust the id in the URL alone). */
    private Feedback ownedFeedback(Long id, User user) {
        Feedback feedback = feedbackService.findById(id);
        if (!feedback.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Feedback not found: " + id);
        }
        return feedback;
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
