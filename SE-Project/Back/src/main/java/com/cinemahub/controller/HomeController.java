package com.cinemahub.controller;

import com.cinemahub.dto.MovieRating;
import com.cinemahub.dto.NowShowingCard;
import com.cinemahub.dto.RatedMovieCard;
import com.cinemahub.dto.StartingSoonCard;
import com.cinemahub.model.Movie;
import com.cinemahub.model.Showtime;
import com.cinemahub.service.FeedbackService;
import com.cinemahub.service.MovieService;
import com.cinemahub.service.FoodComboService;
import com.cinemahub.service.PromotionService;
import com.cinemahub.service.ShowtimeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final MovieService movieService;
    private final ShowtimeService showtimeService;
    private final PromotionService promotionService;
    private final FoodComboService foodComboService;
    private final FeedbackService feedbackService;

    public HomeController(MovieService movieService, ShowtimeService showtimeService,
                          PromotionService promotionService, FoodComboService foodComboService,
                          FeedbackService feedbackService) {
        this.movieService = movieService;
        this.showtimeService = showtimeService;
        this.promotionService = promotionService;
        this.foodComboService = foodComboService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // "Now Showing" = approved movies with at least one upcoming approved showtime.
        // Showtimes come back earliest-first, so the first one seen per movie is its next
        // screening (used for the card's "Buy Tickets" link) and movies are ordered by it.
        Map<Long, Showtime> nextShowtimeByMovie = new LinkedHashMap<>();
        for (Showtime showtime : showtimeService.findUpcomingApproved()) {
            nextShowtimeByMovie.putIfAbsent(showtime.getMovie().getId(), showtime);
        }
        List<NowShowingCard> nowShowing = new ArrayList<>();
        nextShowtimeByMovie.values().forEach(showtime ->
                nowShowing.add(new NowShowingCard(showtime.getMovie(), showtime.getId())));

        // "Coming Soon" = approved, future release date, and not already screening.
        List<Movie> comingSoon = movieService.findComingSoon().stream()
                .filter(movie -> !nextShowtimeByMovie.containsKey(movie.getId()))
                .toList();

        // "Starting Soon": every approved showtime starting in the next 2 hours, soonest first.
        // Empty list = the section isn't rendered at all.
        LocalDateTime now = LocalDateTime.now();
        List<StartingSoonCard> startingSoon = showtimeService.findStartingSoon(now).stream()
                .map(showtime -> new StartingSoonCard(showtime, now))
                .toList();

        model.addAttribute("startingSoon", startingSoon);
        model.addAttribute("nowShowing", nowShowing);
        model.addAttribute("comingSoon", comingSoon);
        // Average star rating per movie id, for the movie cards (missing = "No reviews yet").
        Map<Long, MovieRating> ratings = feedbackService.getRatingsByMovie();
        model.addAttribute("ratings", ratings);
        model.addAttribute("topRated", topRated(ratings, nextShowtimeByMovie));
        // "Promotions" tab: promotion codes customers can use right now, plus food combo packs
        // (approved, available, every included item on the menu).
        model.addAttribute("promotions", promotionService.findAvailableToCustomers());
        model.addAttribute("foodCombos", foodComboService.findOrderable());
        return "home";
    }

    /** How many movies the "Top Rated" tab shows at most. */
    private static final int TOP_RATED_LIMIT = 12;

    /**
     * "Top Rated" tab: approved movies that have at least one rated customer review, best average
     * first (more reviews wins a tie, then title). Movies with no reviews aren't listed at all.
     */
    private List<RatedMovieCard> topRated(Map<Long, MovieRating> ratings, Map<Long, Showtime> nextShowtimeByMovie) {
        return movieService.search(null, null).stream()       // approved movies only
                .filter(movie -> ratings.containsKey(movie.getId()))
                .map(movie -> {
                    Showtime next = nextShowtimeByMovie.get(movie.getId());
                    return new RatedMovieCard(movie, ratings.get(movie.getId()), next != null ? next.getId() : null);
                })
                .sorted(Comparator.comparingDouble((RatedMovieCard card) -> card.rating().average()).reversed()
                        .thenComparing(Comparator.comparingLong((RatedMovieCard card) -> card.rating().count()).reversed())
                        .thenComparing(card -> card.movie().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .limit(TOP_RATED_LIMIT)
                .toList();
    }
}
