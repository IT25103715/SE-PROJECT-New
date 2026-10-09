package com.cinemahub.config;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Feedback;
import com.cinemahub.model.FeedbackType;
import com.cinemahub.model.Movie;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import com.cinemahub.repository.FeedbackRepository;
import com.cinemahub.repository.MovieRepository;
import com.cinemahub.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sample data for the movie ratings feature: four extra customer accounts and a set of rated
 * reviews from them, so the star ratings, the movie pages' review lists and the homepage
 * "Top Rated" tab have something to show.
 *
 * <pre>
 *   customer1@gmail.com / Customer1@123   (Kasun Perera)
 *   customer2@gmail.com / Customer2@123   (Dilini Fernando)
 *   customer3@gmail.com / Customer3@123   (Ashan Silva)
 *   customer4@gmail.com / Customer4@123   (Tharushi Jayasinghe)
 * </pre>
 *
 * Safe to run on every start: an account is only created if its email doesn't exist yet, and a
 * review is only added if that customer hasn't already reviewed that movie. Reviews are only added
 * for approved movies that have already been released (matched by title); missing titles are skipped.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)   // after DataSeeder has created the movies on a fresh database
public class SampleReviewSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleReviewSeeder.class);

    private record SampleCustomer(String name, String email, String password) {
    }

    private record SampleReview(int customer, String movieTitle, int rating, String message, int daysAgo) {
    }

    private static final List<SampleCustomer> CUSTOMERS = List.of(
            new SampleCustomer("Kasun Perera", "customer1@gmail.com", "Customer1@123"),
            new SampleCustomer("Dilini Fernando", "customer2@gmail.com", "Customer2@123"),
            new SampleCustomer("Ashan Silva", "customer3@gmail.com", "Customer3@123"),
            new SampleCustomer("Tharushi Jayasinghe", "customer4@gmail.com", "Customer4@123"));

    // customer = index into CUSTOMERS (1-4)
    private static final List<SampleReview> REVIEWS = List.of(
            new SampleReview(1, "Inception", 5, "Mind-bending from start to finish. The IMAX sound in Hall B made the dream sequences feel huge.", 20),
            new SampleReview(1, "The Dark Knight", 5, "Still the best superhero movie ever made. Heath Ledger's Joker is unforgettable.", 16),
            new SampleReview(1, "Avengers: Endgame", 4, "A great ending to the saga. A bit long, but the final battle was worth every minute.", 12),
            new SampleReview(1, "Master", 4, "Vijay's mass scenes had the whole hall cheering. Second half drags a little.", 9),
            new SampleReview(1, "The Super Mario Galaxy Movie", 4, "Colourful and fun - took my little brother and he loved every second.", 3),

            new SampleReview(2, "Inception", 4, "Clever story and amazing visuals. Had to watch closely to keep up, but loved it.", 18),
            new SampleReview(2, "La La Land", 5, "Beautiful music and that ending stayed with me for days. Perfect date movie.", 15),
            new SampleReview(2, "Parasite", 5, "Brilliant, tense and surprisingly funny. Deserves every award it won.", 11),
            new SampleReview(2, "Toy Story 4", 4, "Sweet and funny, great for the whole family. Forky steals the show.", 7),
            new SampleReview(2, "Despicable Me", 4, "The Minions never get old. Lots of laughs from kids and adults alike.", 2),

            new SampleReview(3, "The Dark Knight", 5, "Gripping, dark and smart. The opening bank heist alone is worth the ticket.", 19),
            new SampleReview(3, "Interstellar", 5, "Emotional and epic. The docking scene with that soundtrack gave me chills.", 14),
            new SampleReview(3, "The Matrix", 4, "A classic that still holds up. The action scenes were ahead of their time.", 10),
            new SampleReview(3, "Get Out", 4, "Creepy and clever - kept me guessing the whole way through.", 6),
            new SampleReview(3, "Aloko Udapadi", 5, "Proud to see a Sri Lankan film on this scale. Stunning battle scenes.", 4),

            new SampleReview(4, "Interstellar", 4, "Long but beautiful. The science bits went over my head, the story didn't.", 17),
            new SampleReview(4, "Avengers: Endgame", 5, "Cried, laughed and cheered. \"Avengers assemble\" moment was incredible.", 13),
            new SampleReview(4, "Soorarai Pottru", 5, "Inspiring true story with a powerful performance by Suriya.", 8),
            new SampleReview(4, "96", 5, "Such a gentle, heartfelt love story. The music is beautiful.", 5),
            new SampleReview(4, "The Matrix", 3, "Good action, but the story was a bit confusing for me.", 1));

    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final FeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;

    public SampleReviewSeeder(UserRepository userRepository, MovieRepository movieRepository,
                              FeedbackRepository feedbackRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.feedbackRepository = feedbackRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // On a brand-new database DataSeeder must go first: it only seeds when there are no users
        // yet, so creating these customers before it would stop it seeding the admin, halls and
        // movies. With no movies yet, wait - the next start adds the sample reviews.
        if (movieRepository.count() == 0) {
            return;
        }
        try {
            List<User> customers = CUSTOMERS.stream().map(this::findOrCreate).toList();

            LocalDate today = LocalDate.now();
            Map<String, Movie> releasedMovies = movieRepository.findByApprovalStatus(ApprovalStatus.APPROVED).stream()
                    .filter(movie -> movie.getReleaseDate() == null || !movie.getReleaseDate().isAfter(today))
                    .collect(Collectors.toMap(movie -> movie.getTitle().toLowerCase(), Function.identity(), (a, b) -> a));

            int added = 0;
            for (SampleReview sample : REVIEWS) {
                Movie movie = releasedMovies.get(sample.movieTitle().toLowerCase());
                if (movie == null) {
                    continue;
                }
                User customer = customers.get(sample.customer() - 1);
                boolean alreadyReviewed = feedbackRepository.findByUser_IdOrderByCreatedAtDesc(customer.getId()).stream()
                        .anyMatch(f -> f.getType() == FeedbackType.REVIEW && f.getMovie() != null
                                && f.getMovie().getId().equals(movie.getId()));
                if (alreadyReviewed) {
                    continue;
                }
                Feedback review = new Feedback();
                review.setUser(customer);
                review.setMovie(movie);
                review.setType(FeedbackType.REVIEW);
                review.setRating(sample.rating());
                review.setMessage(sample.message());
                review.setCreatedAt(LocalDateTime.now().minusDays(sample.daysAgo()));
                feedbackRepository.save(review);
                added++;
            }
            if (added > 0) {
                log.info("Added {} sample movie review(s) from customer1-4@gmail.com", added);
            }
        } catch (Exception ex) {
            // Sample data must never stop the app from starting.
            log.warn("Sample reviews skipped: {}", ex.getMessage());
        }
    }

    private User findOrCreate(SampleCustomer sample) {
        return userRepository.findByEmail(sample.email()).orElseGet(() -> {
            User user = new User();
            user.setName(sample.name());
            user.setEmail(sample.email());
            user.setPassword(passwordEncoder.encode(sample.password()));
            user.setRole(Role.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
            log.info("Created sample customer account {}", sample.email());
            return userRepository.save(user);
        });
    }
}
