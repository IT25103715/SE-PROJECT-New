package com.cinemahub.controller;

import com.cinemahub.model.Language;
import com.cinemahub.model.Movie;
import com.cinemahub.service.FeedbackService;
import com.cinemahub.service.MovieService;
import com.cinemahub.service.ShowtimeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public movie catalogue (Function 2, read side). Anyone - logged in or not
 * - can browse; SecurityConfig permits "/movies/**" to everyone.
 */
@Controller
@RequestMapping("/movies")
public class MovieController {

    /** A fixed, curated set of genre categories to filter by, rather than every raw genre string in the database. */
    private static final List<String> GENRE_CATEGORIES = List.of(
            "Action", "Adventure", "Animation", "Comedy", "Crime", "Drama",
            "Family", "Horror", "Musical", "Romance", "Sci-Fi", "Thriller");

    private final MovieService movieService;
    private final ShowtimeService showtimeService;
    private final FeedbackService feedbackService;

    public MovieController(MovieService movieService, ShowtimeService showtimeService,
                           FeedbackService feedbackService) {
        this.movieService = movieService;
        this.showtimeService = showtimeService;
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Language language,
                        @RequestParam(required = false) String genre,
                        Model model) {
        model.addAttribute("movies", movieService.search(language, genre));
        model.addAttribute("languages", Language.values());
        model.addAttribute("genreCategories", GENRE_CATEGORIES);
        model.addAttribute("selectedLanguage", language);
        model.addAttribute("selectedGenre", genre);
        model.addAttribute("ratings", feedbackService.getRatingsByMovie());
        return "movies/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("movie", movieService.findApprovedById(id));
        // Average of customers' rated reviews + the written reviews themselves
        model.addAttribute("rating", feedbackService.getRating(id));
        model.addAttribute("reviews", feedbackService.findReviewsForMovie(id));
        // Upcoming only - a past showtime can't be booked (BookingService refuses it), so don't offer it.
        model.addAttribute("showtimesByDate", showtimeService.findUpcomingApprovedByMovieGroupedByDate(id));
        return "movies/detail";
    }
}
