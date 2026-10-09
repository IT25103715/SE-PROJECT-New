package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.FoodCombo;
import com.cinemahub.model.FoodComboItem;
import com.cinemahub.model.FoodItem;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.repository.FoodComboRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Food combo packs (bundles of existing food items at a special price). Same approval workflow as
 * FoodItemService: the System Admin's own combos are approved straight away, a Manager's new or
 * edited combo waits in the Approval Queue. Deactivating hides a combo without deleting it.
 */
@Service
public class FoodComboService {

    /** Most of any one item a single combo can contain. */
    public static final int MAX_ITEM_QUANTITY = 20;

    private final FoodComboRepository foodComboRepository;
    private final FoodItemService foodItemService;

    public FoodComboService(FoodComboRepository foodComboRepository, FoodItemService foodItemService) {
        this.foodComboRepository = foodComboRepository;
        this.foodItemService = foodItemService;
    }

    public List<FoodCombo> findAll() {
        return foodComboRepository.findAllByOrderByNameAsc();
    }

    /** What customers can pick at checkout / see on the homepage: approved, available, all items on the menu. */
    public List<FoodCombo> findOrderable() {
        return foodComboRepository.findByAvailableTrueAndApprovalStatusOrderByNameAsc(ApprovalStatus.APPROVED).stream()
                .filter(FoodCombo::isOrderable)
                .toList();
    }

    public List<FoodCombo> findPending() {
        return foodComboRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    public FoodCombo findById(Long id) {
        return foodComboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food combo not found: " + id));
    }

    /**
     * Turns the form's parallel item-id / quantity lists into item id -> quantity, ignoring blank
     * or zero quantities and merging duplicates.
     */
    public static Map<Long, Integer> toQuantities(List<Long> itemIds, List<Integer> quantities) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (itemIds == null || quantities == null) {
            return result;
        }
        for (int i = 0; i < itemIds.size() && i < quantities.size(); i++) {
            Long id = itemIds.get(i);
            Integer quantity = quantities.get(i);
            if (id != null && quantity != null && quantity > 0) {
                result.merge(id, quantity, Integer::sum);
            }
        }
        return result;
    }

    @Transactional
    public FoodCombo create(FoodCombo combo, Map<Long, Integer> itemQuantities, User creator) {
        combo.setId(null);
        combo.setAvailable(true);
        combo.setCreatedBy(creator);
        combo.getItems().clear();
        setItems(combo, itemQuantities);
        validate(combo);
        applyApprovalStatus(combo, creator);
        return foodComboRepository.save(combo);
    }

    @Transactional
    public FoodCombo update(Long id, FoodCombo updated, Map<Long, Integer> itemQuantities, User editor) {
        FoodCombo combo = findById(id);
        combo.setName(updated.getName());
        combo.setDescription(updated.getDescription());
        combo.setBundlePrice(updated.getBundlePrice());
        combo.setCardTheme(updated.getCardTheme());
        combo.getItems().clear();
        setItems(combo, itemQuantities);
        validate(combo);
        applyApprovalStatus(combo, editor);
        return foodComboRepository.save(combo);
    }

    @Transactional
    public void deactivate(Long id) {
        FoodCombo combo = findById(id);
        combo.setAvailable(false);
        foodComboRepository.save(combo);
    }

    @Transactional
    public void reactivate(Long id) {
        FoodCombo combo = findById(id);
        combo.setAvailable(true);
        foodComboRepository.save(combo);
    }

    @Transactional
    public void approve(Long id) {
        FoodCombo combo = findById(id);
        combo.setApprovalStatus(ApprovalStatus.APPROVED);
        combo.setRejectionReason(null);
        foodComboRepository.save(combo);
    }

    @Transactional
    public void reject(Long id, String reason) {
        FoodCombo combo = findById(id);
        combo.setApprovalStatus(ApprovalStatus.REJECTED);
        combo.setRejectionReason(reason);
        foodComboRepository.save(combo);
    }

    private void setItems(FoodCombo combo, Map<Long, Integer> itemQuantities) {
        if (itemQuantities == null || itemQuantities.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one food item (with a quantity) for the combo");
        }
        for (Map.Entry<Long, Integer> entry : itemQuantities.entrySet()) {
            FoodItem foodItem = foodItemService.findById(entry.getKey());
            if (!foodItem.isOrderable()) {
                throw new IllegalArgumentException(foodItem.getName()
                        + " isn't on the menu right now (it must be approved and available) - remove it from the combo");
            }
            FoodComboItem line = new FoodComboItem();
            line.setFoodCombo(combo);
            line.setFoodItem(foodItem);
            line.setQuantity(Math.min(entry.getValue(), MAX_ITEM_QUANTITY));
            combo.getItems().add(line);
        }
    }

    /** The whole point of a combo: it must cost less than buying the same items separately. */
    private void validate(FoodCombo combo) {
        BigDecimal individual = combo.getIndividualTotal();
        if (combo.getBundlePrice() == null || combo.getBundlePrice().compareTo(individual) >= 0) {
            throw new IllegalArgumentException("The combo price must be lower than buying the items separately (Rs. "
                    + individual.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ")");
        }
    }

    // Admin approval required before Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(FoodCombo combo, User actingUser) {
        combo.setApprovalStatus(actingUser != null && actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        combo.setRejectionReason(null);
    }
}
