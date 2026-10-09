package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.FoodItem;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.repository.FoodItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Food menu (Manager CRUD at /manager/food). Same approval workflow as Movies/Showtimes/Halls:
 * a MANAGER's new or edited item is PENDING until SYSTEM_ADMIN approves it in the Approval Queue;
 * SYSTEM_ADMIN's own changes are APPROVED straight away. Delete = soft delete (available=false).
 */
@Service
public class FoodItemService {

    private final FoodItemRepository foodItemRepository;

    public FoodItemService(FoodItemRepository foodItemRepository) {
        this.foodItemRepository = foodItemRepository;
    }

    public List<FoodItem> findAll() {
        return foodItemRepository.findAllByOrderByCategoryAscNameAsc();
    }

    /** What customers can order at checkout: approved and not deactivated. */
    public List<FoodItem> findOrderable() {
        return foodItemRepository.findByAvailableTrueAndApprovalStatusOrderByCategoryAscNameAsc(ApprovalStatus.APPROVED);
    }

    public List<FoodItem> findPending() {
        return foodItemRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    public FoodItem findById(Long id) {
        return foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found: " + id));
    }

    @Transactional
    public FoodItem create(FoodItem item, User creator) {
        item.setId(null);
        item.setAvailable(true);
        item.setCreatedBy(creator);
        item.setImageUrl(blankToNull(item.getImageUrl()));
        applyApprovalStatus(item, creator);
        return foodItemRepository.save(item);
    }

    @Transactional
    public FoodItem update(Long id, FoodItem updated, User editor) {
        FoodItem item = findById(id);
        item.setName(updated.getName());
        item.setCategory(updated.getCategory());
        item.setPrice(updated.getPrice());
        item.setImageUrl(blankToNull(updated.getImageUrl()));
        applyApprovalStatus(item, editor);
        return foodItemRepository.save(item);
    }

    /** "Delete": hides the item from customers but keeps it for past bookings and combo history. */
    @Transactional
    public void deactivate(Long id) {
        FoodItem item = findById(id);
        item.setAvailable(false);
        foodItemRepository.save(item);
    }

    @Transactional
    public void reactivate(Long id) {
        FoodItem item = findById(id);
        item.setAvailable(true);
        foodItemRepository.save(item);
    }

    @Transactional
    public void approve(Long id) {
        FoodItem item = findById(id);
        item.setApprovalStatus(ApprovalStatus.APPROVED);
        item.setRejectionReason(null);
        foodItemRepository.save(item);
    }

    @Transactional
    public void reject(Long id, String reason) {
        FoodItem item = findById(id);
        item.setApprovalStatus(ApprovalStatus.REJECTED);
        item.setRejectionReason(reason);
        foodItemRepository.save(item);
    }

    // Admin approval required before Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(FoodItem item, User actingUser) {
        item.setApprovalStatus(actingUser != null && actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        item.setRejectionReason(null);
    }

    // An empty "Image URL" box means "no photo" - the menu then shows a placeholder.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
