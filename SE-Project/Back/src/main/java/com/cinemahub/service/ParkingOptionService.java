package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.BookingStatus;
import com.cinemahub.model.ParkingOption;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.repository.BookingRepository;
import com.cinemahub.repository.ParkingOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Car parking (Manager CRUD at /manager/parking) - same approval and soft-delete rules as the
 * food menu. Customers are offered the first approved, available option; its totalSlots is the
 * number of parking spaces per showtime.
 */
@Service
public class ParkingOptionService {

    private final ParkingOptionRepository parkingOptionRepository;
    private final BookingRepository bookingRepository;

    public ParkingOptionService(ParkingOptionRepository parkingOptionRepository, BookingRepository bookingRepository) {
        this.parkingOptionRepository = parkingOptionRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<ParkingOption> findAll() {
        return parkingOptionRepository.findAllByOrderByIdAsc();
    }

    public List<ParkingOption> findPending() {
        return parkingOptionRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    /** The parking customers can add at checkout (approved + available), if any. */
    public Optional<ParkingOption> findOrderable() {
        return parkingOptionRepository.findByAvailableTrueAndApprovalStatusOrderByIdAsc(ApprovalStatus.APPROVED)
                .stream().findFirst();
    }

    /**
     * Parking spaces still free for one showtime: totalSlots minus the CONFIRMED bookings for that
     * showtime that already added parking. 0 when there is no orderable parking option.
     */
    public int slotsLeft(Long showtimeId) {
        return findOrderable()
                .map(option -> (int) Math.max(0, option.getTotalSlots()
                        - bookingRepository.countByShowtime_IdAndStatusAndParkingSelectedTrue(showtimeId, BookingStatus.CONFIRMED)))
                .orElse(0);
    }

    public ParkingOption findById(Long id) {
        return parkingOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking option not found: " + id));
    }

    @Transactional
    public ParkingOption create(ParkingOption option, User creator) {
        option.setId(null);
        option.setAvailable(true);
        option.setCreatedBy(creator);
        applyApprovalStatus(option, creator);
        return parkingOptionRepository.save(option);
    }

    @Transactional
    public ParkingOption update(Long id, ParkingOption updated, User editor) {
        ParkingOption option = findById(id);
        option.setLabel(updated.getLabel());
        option.setPrice(updated.getPrice());
        option.setTotalSlots(updated.getTotalSlots());
        applyApprovalStatus(option, editor);
        return parkingOptionRepository.save(option);
    }

    @Transactional
    public void deactivate(Long id) {
        ParkingOption option = findById(id);
        option.setAvailable(false);
        parkingOptionRepository.save(option);
    }

    @Transactional
    public void reactivate(Long id) {
        ParkingOption option = findById(id);
        option.setAvailable(true);
        parkingOptionRepository.save(option);
    }

    @Transactional
    public void approve(Long id) {
        ParkingOption option = findById(id);
        option.setApprovalStatus(ApprovalStatus.APPROVED);
        option.setRejectionReason(null);
        parkingOptionRepository.save(option);
    }

    @Transactional
    public void reject(Long id, String reason) {
        ParkingOption option = findById(id);
        option.setApprovalStatus(ApprovalStatus.REJECTED);
        option.setRejectionReason(reason);
        parkingOptionRepository.save(option);
    }

    // Admin approval required before Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(ParkingOption option, User actingUser) {
        option.setApprovalStatus(actingUser != null && actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        option.setRejectionReason(null);
    }
}
