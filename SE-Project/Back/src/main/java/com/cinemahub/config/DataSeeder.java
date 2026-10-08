package com.cinemahub.config;

import com.cinemahub.model.*;
import com.cinemahub.repository.BookingRepository;
import com.cinemahub.repository.MovieRepository;
import com.cinemahub.repository.PaymentOptionRepository;
import com.cinemahub.repository.PaymentRepository;
import com.cinemahub.repository.PromotionRepository;
import com.cinemahub.repository.ShowtimeRepository;
import com.cinemahub.repository.UserRepository;
import com.cinemahub.service.CinemaHallService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Populates the database with a demo-ready dataset on first startup, so the
 * app can be shown off (or graded) without manually creating accounts and
 * movies first. Guarded by userRepository.count() == 0 so it only ever runs
 * once - after that, Hibernate's ddl-auto=update keeps the schema but this
 * seeder no longer touches the data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentOptionRepository paymentOptionRepository;
    private final CinemaHallService cinemaHallService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                       MovieRepository movieRepository,
                       ShowtimeRepository showtimeRepository,
                       BookingRepository bookingRepository,
                       PaymentRepository paymentRepository,
                       PromotionRepository promotionRepository,
                       PaymentOptionRepository paymentOptionRepository,
                       CinemaHallService cinemaHallService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.showtimeRepository = showtimeRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.promotionRepository = promotionRepository;
        this.paymentOptionRepository = paymentOptionRepository;
        this.cinemaHallService = cinemaHallService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = seedUser("System Admin", "admin@gmail.com", "Admin@123", Role.SYSTEM_ADMIN);
        seedUser("Movie Manager", "manager@gmail.com", "Manager@123", Role.MANAGER);
        seedUser("Customer Relations", "cre@gmail.com", "Cre@12345", Role.CUSTOMER_RELATIONS_EXECUTIVE);
        seedUser("Promotion Manager", "promo@gmail.com", "Promo@123", Role.PROMOTION_MANAGER);
        // Converted from the old "staff@cinemahub.lk" CINEMA_STAFF test account - Cinema Staff
        // coverage is kept by the two per-module logins below (staff.movies/staff.ticketing).
        seedUser("Payment Manager", "paymentmanager@gmail.com", "PaymentManager@123", Role.PAYMENT_MANAGER);
        User customer = seedUser("Nimal Perera", "customer@gmail.com", "Customer@123", Role.CUSTOMER);

        // Extra per-module demo logins (same role/permissions as the accounts above,
        // just a distinct login per team member's module for viva clarity) - see TEAM_STATUS.md.
        seedUser("Cinema Staff (Movies)", "staff.movies@gmail.com", "Staff@123", Role.CINEMA_STAFF);
        seedUser("Cinema Staff (Ticketing)", "staff.ticketing@gmail.com", "Staff@123", Role.CINEMA_STAFF);

        CinemaHall hallA = new CinemaHall();
        hallA.setName("Hall A");
        hallA.setTotalRows(5);
        hallA.setSeatsPerRow(8);
        // Seeded by the admin account, so it's saved as APPROVED directly - see CinemaHallService.
        hallA = cinemaHallService.save(hallA, admin);

        CinemaHall hallB = new CinemaHall();
        hallB.setName("Hall B (IMAX)");
        hallB.setTotalRows(6);
        hallB.setSeatsPerRow(10);
        hallB = cinemaHallService.save(hallB, admin);

        Movie inception = seedMovie("Inception", "Sci-Fi / Thriller", Language.ENGLISH,
                "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea.",
                148, "https://image.tmdb.org/t/p/w500/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg");
        Movie interstellar = seedMovie("Interstellar", "Sci-Fi / Drama", Language.ENGLISH,
                "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
                169, "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg");
        Movie darkKnight = seedMovie("The Dark Knight", "Action / Crime", Language.ENGLISH,
                "Batman raises the stakes in his war on crime, facing the menace known as the Joker.",
                152, "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg");
        Movie matrix = seedMovie("The Matrix", "Sci-Fi / Action", Language.ENGLISH,
                "A computer hacker learns from mysterious rebels about the true nature of his reality and his role in the war against its controllers.",
                136, "https://image.tmdb.org/t/p/w500/aOIuZAjPaRIE6CMzbazvcHuHXDc.jpg");
        Movie endgame = seedMovie("Avengers: Endgame", "Action / Adventure", Language.ENGLISH,
                "The surviving Avengers assemble once more to reverse the damage caused by Thanos and restore order to the universe.",
                181, "https://image.tmdb.org/t/p/w500/mLxEbo3yxFTfQ1TB2HMu5cGHTN3.jpg");
        Movie lalaland = seedMovie("La La Land", "Romance / Musical", Language.ENGLISH,
                "An aspiring actress and a dedicated jazz musician fall in love while pursuing their dreams in Los Angeles.",
                128, "https://image.tmdb.org/t/p/w500/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg");
        Movie parasite = seedMovie("Parasite", "Thriller / Drama", Language.ENGLISH,
                "Greed and class discrimination threaten the newly formed relationship between a wealthy family and a destitute clan.",
                133, "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg");
        Movie getOut = seedMovie("Get Out", "Horror / Thriller", Language.ENGLISH,
                "A young man visits his girlfriend's family estate, where he discovers a disturbing secret involving their community.",
                104, "https://image.tmdb.org/t/p/w500/tFXcEccSQMf3lfhfXKSU9iRBpa3.jpg");
        Movie toyStory4 = seedMovie("Toy Story 4", "Animation / Family", Language.ENGLISH,
                "Woody and the gang embark on a road trip with new toy Forky, discovering what it means to be a lost toy.",
                100, "https://image.tmdb.org/t/p/w500/w9kR8qbmQ01HwnvK4alvnQ2ca0L.jpg");
        Movie alokoUdapadi = seedMovie("Aloko Udapadi", "Drama / History", Language.SINHALA,
                "A Sinhalese epic historical film based on the story of King Valagamba of Anuradhapura.",
                150, "https://image.tmdb.org/t/p/w500/ccYZOP6Vt7O23DS1NpIA53OD5VV.jpg");
        Movie sriSiddhartha = seedMovie("Sri Siddhartha Gautama", "Drama / History", Language.SINHALA,
                "A big-budget Sinhala biographical epic depicting the life of Prince Siddhartha's journey to enlightenment as the Buddha.",
                180, "https://image.tmdb.org/t/p/w500/qNIwSWx27sjICecBZd885fbalV6.jpg");
        Movie master = seedMovie("Master", "Action / Thriller", Language.TAMIL,
                "An alcoholic professor is sent to a juvenile school, where he clashes with a gangster who uses the school kids for illegal activities.",
                179, "https://image.tmdb.org/t/p/w500/wjbOlovDadOdPKkSAMohLCjbIsc.jpg");
        Movie ninetySix = seedMovie("96", "Romance / Drama", Language.TAMIL,
                "A man and woman, separated since school, reconnect at a reunion and revisit their unspoken love from 1996.",
                158, "https://image.tmdb.org/t/p/w500/nrVloCa2hCFOztRF1DZU2jnWIiQ.jpg");
        Movie soorarai = seedMovie("Soorarai Pottru", "Drama / Adventure", Language.TAMIL,
                "A man's relentless struggle to start his own low-cost airline against a powerful business rival.",
                153, "https://image.tmdb.org/t/p/w500/5uimlxPCgAei8JfQUDFEUQLoyyh.jpg");

        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        Showtime inceptionMatinee = seedShowtime(inception, hallA, now.plusDays(1).withHour(14), new BigDecimal("1200.00"));
        Showtime inceptionEvening = seedShowtime(inception, hallB, now.plusDays(1).withHour(19), new BigDecimal("1500.00"));
        Showtime interstellarShow = seedShowtime(interstellar, hallA, now.plusDays(2).withHour(16), new BigDecimal("1200.00"));
        Showtime darkKnightShow = seedShowtime(darkKnight, hallB, now.plusDays(2).withHour(20), new BigDecimal("1500.00"));
        seedShowtime(matrix, hallA, now.plusDays(1).withHour(17), new BigDecimal("1200.00"));
        seedShowtime(endgame, hallB, now.plusDays(2).withHour(18), new BigDecimal("1500.00"));
        seedShowtime(lalaland, hallA, now.plusDays(3).withHour(15), new BigDecimal("1000.00"));
        seedShowtime(parasite, hallA, now.plusDays(3).withHour(19), new BigDecimal("1200.00"));
        seedShowtime(getOut, hallB, now.plusDays(1).withHour(21), new BigDecimal("1200.00"));
        seedShowtime(toyStory4, hallB, now.plusDays(3).withHour(13), new BigDecimal("1000.00"));
        seedShowtime(alokoUdapadi, hallB, now.plusDays(4).withHour(15), new BigDecimal("900.00"));
        seedShowtime(sriSiddhartha, hallA, now.plusDays(4).withHour(18), new BigDecimal("900.00"));
        seedShowtime(master, hallA, now.plusDays(2).withHour(21), new BigDecimal("1000.00"));
        seedShowtime(ninetySix, hallB, now.plusDays(4).withHour(20), new BigDecimal("1000.00"));
        seedShowtime(soorarai, hallA, now.plusDays(5).withHour(17), new BigDecimal("1000.00"));

        // Homepage "Coming Soon" tab: approved movies with a future release date and no showtimes yet.
        Movie klara = seedMovie("Klara and the Sun", "Sci-Fi / Drama", Language.ENGLISH,
                "Directed by Taika Waititi, an adaptation of Kazuo Ishiguro's novel about an artificial friend, starring Jenna Ortega and Amy Adams.",
                116, "https://upload.wikimedia.org/wikipedia/en/7/77/Klara_and_the_Sun_%28film%29_poster.jpg");
        klara.setReleaseDate(LocalDate.of(2026, 10, 23));
        movieRepository.save(klara);
        Movie doomsday = seedMovie("Avengers: Doomsday", "Action / Superhero", Language.ENGLISH,
                "The 39th Marvel Cinematic Universe film and the sequel to Avengers: Endgame, directed by Anthony and Joe Russo.",
                165, "https://upload.wikimedia.org/wikipedia/en/e/ee/Avengers_Doomsday_poster.jpg");
        doomsday.setReleaseDate(LocalDate.of(2026, 12, 18));
        movieRepository.save(doomsday);

        // A handful of sample transactions for the Payment & Transaction Management
        // module (Function 4) - one of each interesting state, so the manager
        // "Payments & Revenue" screen and the customer "My Payment History"
        // screen both have something to show out of the box.
        seedTransaction(customer, inceptionMatinee, hallA.getSeats().subList(0, 2),
                BookingStatus.CONFIRMED, false, PaymentMethod.CARD, PaymentStatus.COMPLETED, false);
        seedTransaction(customer, darkKnightShow, hallB.getSeats().subList(0, 2),
                BookingStatus.CONFIRMED, false, PaymentMethod.CASH, PaymentStatus.COMPLETED, false);
        seedTransaction(customer, interstellarShow, hallA.getSeats().subList(2, 3),
                BookingStatus.CANCELLED, true, PaymentMethod.SIMULATED_GATEWAY, PaymentStatus.REFUNDED, false);
        seedTransaction(customer, inceptionEvening, hallB.getSeats().subList(2, 3),
                BookingStatus.CANCELLED, false, PaymentMethod.CARD, PaymentStatus.FAILED, true);

        // Function 6 demo data: one usable coupon of each discount type, plus
        // one already-disabled coupon so the manage screen shows every status.
        seedPromotion("WELCOME10", "10% off your first booking", DiscountType.PERCENTAGE,
                new BigDecimal("10"), LocalDate.now().minusDays(5), LocalDate.now().plusMonths(1), 100, PromotionStatus.ACTIVE);
        seedPromotion("FLAT500", "Rs. 500 off bookings over Rs. 2000", DiscountType.FIXED_AMOUNT,
                new BigDecimal("500"), LocalDate.now().minusDays(5), LocalDate.now().plusMonths(1), null, PromotionStatus.ACTIVE);
        seedPromotion("OLDPROMO20", "Expired new-year promotion", DiscountType.PERCENTAGE,
                new BigDecimal("20"), LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(2), 50, PromotionStatus.EXPIRED);

        // Payment Manager demo data: a few accepted payment options, one already
        // disabled so the dashboard demoes both the CRUD screen and the
        // disabled-options-hidden-from-checkout filtering in one go.
        PaymentOption applePay = paymentOption("Apple Pay", PaymentOptionType.APPLE_PAY, PaymentOptionStatus.ACTIVE);
        applePay.setMerchantId("merchant.lk.cinemaxlanka");
        applePay.setMerchantDisplayName("CinemaX Lanka");
        paymentOptionRepository.save(applePay);

        // Public Client ID placeholder only - the real Sandbox ID + secret come from config/application.properties.
        PaymentOption payPal = paymentOption("PayPal", PaymentOptionType.PAYPAL, PaymentOptionStatus.ACTIVE);
        payPal.setClientId("set-your-sandbox-client-id");
        paymentOptionRepository.save(payPal);

        PaymentOption bankTransfer = paymentOption("Bank Transfer", PaymentOptionType.BANK_TRANSFER, PaymentOptionStatus.ACTIVE);
        bankTransfer.setBankName("Commercial Bank of Ceylon");
        bankTransfer.setBranchName("Colombo 03");
        bankTransfer.setAccountHolderName("CinemaX Lanka (Pvt) Ltd");
        bankTransfer.setAccountNumber("1000123456");
        bankTransfer.setBranchCode("7056-003");
        bankTransfer.setSwiftCode("CCEYLKLX");
        bankTransfer.setPaymentReferenceInstructions("Please use your Booking ID as the payment reference.");
        paymentOptionRepository.save(bankTransfer);

        seedPaymentOption("Mastercard", PaymentOptionType.CARD_NETWORK, "Merchant ID MC-MID-30947", PaymentOptionStatus.DISABLED);

        System.out.println("=======================================================");
        System.out.println(" CinemaHub demo data loaded.");
        System.out.println(" Admin login: " + admin.getEmail() + " / Admin@123");
        System.out.println(" (see README.md for the rest of the demo accounts)");
        System.out.println("=======================================================");
    }

    private User seedUser(String name, String email, String rawPassword, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private Movie seedMovie(String title, String genre, Language language, String description, int durationMinutes, String posterUrl) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setLanguage(language);
        movie.setDescription(description);
        movie.setDurationMinutes(durationMinutes);
        movie.setPosterUrl(posterUrl);
        // Seed catalogue is system data, not a Manager submission - skip the admin approval queue.
        movie.setApprovalStatus(ApprovalStatus.APPROVED);
        return movieRepository.save(movie);
    }

    private void seedPromotion(String code, String description, DiscountType discountType, BigDecimal discountValue,
                                LocalDate startDate, LocalDate endDate, Integer usageLimit, PromotionStatus status) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setDescription(description);
        promotion.setDiscountType(discountType);
        promotion.setDiscountValue(discountValue);
        promotion.setStartDate(startDate);
        promotion.setEndDate(endDate);
        promotion.setUsageLimit(usageLimit);
        promotion.setStatus(status);
        // Seed data is system data, not a Promotion Manager submission - skip the admin approval queue.
        promotion.setApprovalStatus(ApprovalStatus.APPROVED);
        promotionRepository.save(promotion);
    }

    private PaymentOption paymentOption(String name, PaymentOptionType type, PaymentOptionStatus status) {
        PaymentOption paymentOption = new PaymentOption();
        paymentOption.setName(name);
        paymentOption.setType(type);
        paymentOption.setStatus(status);
        return paymentOption;
    }

    private void seedPaymentOption(String name, PaymentOptionType type, String providerDetails, PaymentOptionStatus status) {
        PaymentOption paymentOption = new PaymentOption();
        paymentOption.setName(name);
        paymentOption.setType(type);
        paymentOption.setProviderDetails(providerDetails);
        paymentOption.setStatus(status);
        paymentOptionRepository.save(paymentOption);
    }

    private Showtime seedShowtime(Movie movie, CinemaHall hall, LocalDateTime dateTime, BigDecimal price) {
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setCinemaHall(hall);
        showtime.setDateTime(dateTime);
        showtime.setPrice(price);
        // Seed catalogue is system data, not a Manager submission - skip the admin approval queue.
        showtime.setApprovalStatus(ApprovalStatus.APPROVED);
        return showtimeRepository.save(showtime);
    }

    /**
     * Builds a Booking (with its seats) and its matching Payment directly via
     * the repositories, bypassing BookingService/PaymentService - this is
     * seed data representing an already-completed history, not a live
     * booking flow, so there's no seat-availability race or notification to
     * simulate here.
     */
    private void seedTransaction(User customer, Showtime showtime, List<Seat> seats,
                                  BookingStatus bookingStatus, boolean refunded,
                                  PaymentMethod method, PaymentStatus paymentStatus, boolean archived) {
        BigDecimal total = showtime.getPrice().multiply(BigDecimal.valueOf(seats.size()));

        Booking booking = new Booking();
        booking.setUser(customer);
        booking.setShowtime(showtime);
        booking.setStatus(bookingStatus);
        booking.setTotalPrice(total);
        booking.setRefunded(refunded);
        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setSeat(seat);
            booking.getBookingSeats().add(bookingSeat);
        }
        Booking savedBooking = bookingRepository.save(booking);

        Payment payment = new Payment();
        payment.setBooking(savedBooking);
        payment.setAmount(total);
        payment.setMethod(method);
        payment.setStatus(paymentStatus);
        payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setArchived(archived);
        paymentRepository.save(payment);
    }
}
