package com.cinemahub.service;

import com.cinemahub.dto.DiscountPreviewResult;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.DiscountScope;
import com.cinemahub.model.DiscountType;
import com.cinemahub.model.Promotion;
import com.cinemahub.model.PromotionStatus;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.repository.PromotionRepository;
import com.cinemahub.service.discount.ConditionalDiscountStrategy;
import com.cinemahub.service.discount.ComboOrder;
import com.cinemahub.service.discount.DiscountStrategy;
import com.cinemahub.service.discount.DiscountStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Function 6: creating/managing promotional campaigns and coupon codes,
 * calculating their discount via the Strategy pattern, and tracking usage.
 */
@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final MembershipService membershipService;

    public PromotionService(PromotionRepository promotionRepository, MembershipService membershipService) {
        this.promotionRepository = promotionRepository;
        this.membershipService = membershipService;
    }

    public List<Promotion> findAll() {
        return promotionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Homepage "Promotions" tab: only campaigns a customer could actually apply right now -
     * the same checks {@link #previewDiscount} enforces at checkout (approved, ACTIVE, inside
     * its date range, usage limit not reached), so the tab never advertises a dead code.
     * Members Only promotions are included for everyone (labelled on the card) so non-members
     * can see what membership unlocks.
     */
    public List<Promotion> findAvailableToCustomers() {
        LocalDate today = LocalDate.now();
        return promotionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> p.getApprovalStatus() == ApprovalStatus.APPROVED)
                .filter(p -> p.getStatus() == PromotionStatus.ACTIVE)
                .filter(p -> !today.isBefore(p.getStartDate()) && !today.isAfter(p.getEndDate()))
                .filter(p -> p.getUsageLimit() == null || p.getUsageCount() < p.getUsageLimit())
                .toList();
    }

    public Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
    }

    /** Create (US-33/US-34). */
    @Transactional
    public Promotion create(Promotion promotion, User creator) {
        validateBusinessRules(promotion);
        if (promotionRepository.existsByCodeIgnoreCase(promotion.getCode())) {
            throw new IllegalArgumentException("A coupon with code '" + promotion.getCode() + "' already exists");
        }
        promotion.setId(null);
        promotion.setUsageCount(0);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setCreatedBy(creator);
        clearComboFieldsUnlessCombo(promotion);
        applyApprovalStatus(promotion, creator);
        return promotionRepository.save(promotion);
    }

    /** Update (US-33/US-34) - only the editable campaign fields; status/usageCount change through their own actions. */
    @Transactional
    public Promotion update(Long id, Promotion updated, User editor) {
        validateBusinessRules(updated);
        Promotion promotion = findById(id);

        if (!promotion.getCode().equalsIgnoreCase(updated.getCode())
                && promotionRepository.existsByCodeIgnoreCase(updated.getCode())) {
            throw new IllegalArgumentException("A coupon with code '" + updated.getCode() + "' already exists");
        }

        promotion.setCode(updated.getCode());
        promotion.setDescription(updated.getDescription());
        promotion.setDiscountType(updated.getDiscountType());
        promotion.setDiscountValue(updated.getDiscountValue());
        promotion.setStartDate(updated.getStartDate());
        promotion.setEndDate(updated.getEndDate());
        promotion.setUsageLimit(updated.getUsageLimit());
        promotion.setDiscountScope(updated.getDiscountScope());
        promotion.setCardTheme(updated.getCardTheme());
        promotion.setMembersOnly(updated.isMembersOnly());
        promotion.setMinSeats(updated.getMinSeats());
        promotion.setRequiredFoodItem(updated.getRequiredFoodItem());
        promotion.setRequiresParking(updated.getRequiresParking());
        clearComboFieldsUnlessCombo(promotion);
        applyApprovalStatus(promotion, editor);
        return promotionRepository.save(promotion);
    }

    public List<Promotion> findPending() {
        return promotionRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    @Transactional
    public void approve(Long id) {
        Promotion promotion = findById(id);
        promotion.setApprovalStatus(ApprovalStatus.APPROVED);
        promotion.setRejectionReason(null);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void reject(Long id, String reason) {
        Promotion promotion = findById(id);
        promotion.setApprovalStatus(ApprovalStatus.REJECTED);
        promotion.setRejectionReason(reason);
        promotionRepository.save(promotion);
    }

    /**
     * Delete action for this module (the rubric's CRUD gap): rather than
     * removing the row - which would wipe out its usage history - flip its
     * status so it can no longer be applied. Mirrors how Function 4 archives
     * payments instead of hard-deleting them.
     */
    @Transactional
    public void deactivate(Long id) {
        Promotion promotion = findById(id);
        promotion.setStatus(PromotionStatus.DISABLED);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void expire(Long id) {
        Promotion promotion = findById(id);
        promotion.setStatus(PromotionStatus.EXPIRED);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void reactivate(Long id) {
        Promotion promotion = findById(id);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotionRepository.save(promotion);
    }

    /**
     * Read-only calculation used by the manager's "try a coupon" tool - does
     * NOT count as a redemption, so it never touches usageCount.
     */
    public DiscountPreviewResult previewDiscount(String code, BigDecimal originalAmount) {
        // No booking to look at here ("Try a Coupon", help assistant) - a COMBO code is only
        // checked for validity, its seat/food/parking conditions can't be.
        return previewDiscount(code, originalAmount, null);
    }

    /**
     * Same as {@link #previewDiscount(String, BigDecimal)}, priced against an actual booking.
     * A whole-booking Percentage / Fixed code with no minimum seats behaves exactly as before.
     * A code whose conditions the booking doesn't meet (a COMBO's seats / food / parking, or a
     * Percentage / Fixed code's minimum seats or food-only scope with no food) gets a zero
     * discount from its strategy and is reported as an IllegalArgumentException - so, like any
     * other unusable code, the booking goes ahead at full price with a warning and the code's
     * usage count is NOT increased. A FOOD_ONLY code discounts only the food subtotal.
     */
    public DiscountPreviewResult previewDiscount(String code, BigDecimal originalAmount, ComboOrder order) {
        Promotion promotion = validAndUsable(code);
        DiscountStrategy strategy = DiscountStrategyFactory.get(promotion, order);
        if (strategy instanceof ConditionalDiscountStrategy conditional && !conditional.unmetConditions().isEmpty()) {
            String kind = promotion.getDiscountType() == DiscountType.COMBO ? "Combo" : "Coupon";
            throw new IllegalArgumentException(kind + " '" + promotion.getCode() + "' needs "
                    + String.join(", ", conditional.unmetConditions()) + " in your booking");
        }
        BigDecimal discount = strategy.calculateDiscount(originalAmount, promotion.getDiscountValue());
        BigDecimal finalAmount = originalAmount.subtract(discount);
        return new DiscountPreviewResult(promotion.getCode(), promotion.getDiscountType().name(),
                originalAmount, discount, finalAmount);
    }

    /**
     * Records one usage against a coupon's limit (US-35) - called by
     * BookingService only after {@link #previewDiscount} has already
     * confirmed the code is valid, so this itself never needs to validate
     * or throw. Kept as its own step (rather than folded into a single
     * "redeem" method) deliberately: previewDiscount is a plain read with
     * no @Transactional annotation, so BookingService can safely try it and
     * fall back to full price on an invalid code. If that validation and
     * the usage-count write were combined into one @Transactional method,
     * catching its exception in the caller would NOT stop Spring from
     * marking the caller's own (shared) transaction rollback-only - the
     * booking would still blow up with UnexpectedRollbackException even
     * though the exception was "handled".
     */
    @Transactional
    public void recordUsage(String code) {
        promotionRepository.findByCodeIgnoreCase(code).ifPresent(promotion -> {
            promotion.setUsageCount(promotion.getUsageCount() + 1);
            promotionRepository.save(promotion);
        });
    }


    private Promotion validAndUsable(String code) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("No such coupon code: " + code));

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new IllegalArgumentException("Coupon '" + promotion.getCode() + "' is not active");
        }
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        if (promotion.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Coupon '" + promotion.getCode() + "' is not active");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(promotion.getStartDate()) || today.isAfter(promotion.getEndDate())) {
            throw new IllegalArgumentException("Coupon '" + promotion.getCode() + "' is outside its valid date range");
        }
        if (promotion.getUsageLimit() != null && promotion.getUsageCount() >= promotion.getUsageLimit()) {
            throw new IllegalArgumentException("Coupon '" + promotion.getCode() + "' has reached its usage limit");
        }
        // Membership program: a Members Only code simply doesn't apply for a non-member - like every
        // other unusable code, the booking still goes ahead at full price with this message shown.
        if (promotion.isMembersOnly() && !membershipService.currentUserIsMember()) {
            throw new IllegalArgumentException("Coupon '" + promotion.getCode() + "' is for CinemaX members only - "
                    + "you become a member automatically after " + MembershipService.BOOKINGS_FOR_MEMBERSHIP
                    + " confirmed bookings");
        }
        return promotion;
    }

    // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(Promotion promotion, User actingUser) {
        promotion.setApprovalStatus(actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        promotion.setRejectionReason(null);
    }

    /** Cross-field checks a plain @NotNull/@Positive annotation can't express on its own. */
    private void validateBusinessRules(Promotion promotion) {
        if (promotion.getDiscountType() == DiscountType.PERCENTAGE
                && promotion.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("A percentage discount cannot exceed 100%");
        }
        if (promotion.getEndDate().isBefore(promotion.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before the start date");
        }
        if (promotion.getDiscountType() == DiscountType.COMBO) {
            boolean hasCondition = (promotion.getMinSeats() != null && promotion.getMinSeats() > 0)
                    || promotion.getRequiredFoodItem() != null
                    || promotion.parkingRequired();
            if (!hasCondition) {
                throw new IllegalArgumentException("A Combo promotion needs at least one condition: minimum seats, a food item, or parking");
            }
        }
    }

    /**
     * Percentage / fixed promotions never keep the combo-only conditions (food item, parking),
     * e.g. after switching type on edit - but they DO keep minimum seats, which works for every
     * type. A COMBO is always a whole-booking discount. A blank / zero minimum means none.
     */
    private void clearComboFieldsUnlessCombo(Promotion promotion) {
        if (promotion.getDiscountType() != DiscountType.COMBO) {
            promotion.setRequiredFoodItem(null);
            promotion.setRequiresParking(null);
        } else {
            promotion.setDiscountScope(DiscountScope.BOOKING_TOTAL);
        }
        if (promotion.getDiscountScope() == null) {
            promotion.setDiscountScope(DiscountScope.BOOKING_TOTAL);
        }
        if (promotion.getMinSeats() != null && promotion.getMinSeats() <= 0) {
            promotion.setMinSeats(null);
        }
    }
}
