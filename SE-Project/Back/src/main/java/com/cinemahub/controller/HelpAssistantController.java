package com.cinemahub.controller;

import com.cinemahub.dto.DiscountPreviewResult;
import com.cinemahub.model.Showtime;
import com.cinemahub.service.PromotionService;
import com.cinemahub.service.ShowtimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend for the "Need Help?" widget (fragments/help-assistant.html) - a simple RULE-BASED
 * assistant, not an AI: the customer clicks one of a fixed list of questions, static answers are
 * plain text in the page, and only these two "live" questions call the server.
 *
 * Both endpoints are READ-ONLY and only reuse existing service methods - nothing is created,
 * updated or deleted, and no external API is called:
 * <ul>
 *   <li>{@link #showingToday()} - {@link ShowtimeService#findUpcomingApproved()}, the exact method
 *       behind the homepage's "Now Showing" (approved movies + approved, not-yet-started showtimes).</li>
 *   <li>{@link #checkPromo(String)} - {@link PromotionService#previewDiscount}, the same validation
 *       BookingService runs at checkout. previewDiscount never records a usage, so checking a
 *       code here does not use it up.</li>
 * </ul>
 * Public under "/help/**" (SecurityConfig) so logged-out visitors can use it too.
 */
@RestController
@RequestMapping("/help")
public class HelpAssistantController {

    private static final int MAX_MOVIES = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");

    private final ShowtimeService showtimeService;
    private final PromotionService promotionService;

    public HelpAssistantController(ShowtimeService showtimeService, PromotionService promotionService) {
        this.showtimeService = showtimeService;
        this.promotionService = promotionService;
    }

    /**
     * Up to 5 movies with their next showtime today. If nothing is left today, the next upcoming
     * screenings instead (flagged with today=false so the widget can say so).
     */
    @GetMapping("/showing-today")
    public Map<String, Object> showingToday() {
        List<Showtime> upcoming = showtimeService.findUpcomingApproved();
        LocalDate today = LocalDate.now();
        List<Showtime> todays = upcoming.stream()
                .filter(showtime -> showtime.getDateTime().toLocalDate().equals(today))
                .toList();
        boolean isToday = !todays.isEmpty();
        List<Showtime> source = isToday ? todays : upcoming;

        // One line per movie: its earliest remaining showtime (lists are already earliest-first).
        Map<Long, Showtime> firstPerMovie = new LinkedHashMap<>();
        for (Showtime showtime : source) {
            firstPerMovie.putIfAbsent(showtime.getMovie().getId(), showtime);
            if (firstPerMovie.size() == MAX_MOVIES) {
                break;
            }
        }
        List<Map<String, Object>> movies = new ArrayList<>();
        for (Showtime showtime : firstPerMovie.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", showtime.getMovie().getTitle());
            item.put("time", showtime.getDateTime().format(isToday ? TIME : DAY_TIME));
            item.put("hall", showtime.getCinemaHall() != null ? showtime.getCinemaHall().getName() : null);
            item.put("showtimeId", showtime.getId());
            movies.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("today", isToday);
        body.put("movies", movies);
        return body;
    }

    /** Is this coupon usable right now? Same rule as checkout; never counts as a use. */
    @GetMapping("/promo-check")
    public Map<String, Object> checkPromo(@RequestParam(required = false) String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty() || trimmed.length() > 50) {
            body.put("valid", false);
            body.put("message", "Please type a promo code to check.");
            return body;
        }
        try {
            // Any positive amount works - only the validity answer is used, not the discount figure.
            DiscountPreviewResult preview = promotionService.previewDiscount(trimmed, new BigDecimal("1000.00"));
            body.put("valid", true);
            body.put("code", preview.getCode());
            body.put("message", "Good news - " + preview.getCode()
                    + " is valid and can be used at checkout right now.");
        } catch (IllegalArgumentException ex) {
            body.put("valid", false);
            body.put("message", ex.getMessage() + ". You can still book - it would just go through at full price.");
        }
        return body;
    }
}
