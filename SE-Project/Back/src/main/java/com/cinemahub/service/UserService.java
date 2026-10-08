package com.cinemahub.service;

import com.cinemahub.dto.AccountForm;
import com.cinemahub.dto.RegistrationDto;
import com.cinemahub.dto.UserForm;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import com.cinemahub.repository.UserRepository;
import com.cinemahub.validation.PhoneNumberRule;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Business logic for Function 1: accounts, roles, and (since Function 4 was
 * repurposed to Payment & Transaction Management) the handful of basic
 * contact fields that used to live on the standalone Profile entity.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /** Public self-registration - always creates a CUSTOMER account. */
    @Transactional
    public User registerCustomer(RegistrationDto dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        PhoneNumberRule.requireAtLeastOne(dto);

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(PhoneNumberRule.clean(dto.getPhoneNumber()));
        user.setPhoneNumberAlt(PhoneNumberRule.clean(dto.getPhoneNumberAlt()));
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    /** SYSTEM_ADMIN creating an account for staff/manager/etc. */
    @Transactional
    public User createUser(UserForm form) {
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for a new account");
        }
        PhoneNumberRule.requireAtLeastOne(form);

        User user = new User();
        user.setName(form.getName());
        user.setEmail(form.getEmail());
        user.setPhoneNumber(PhoneNumberRule.clean(form.getPhoneNumber()));
        user.setPhoneNumberAlt(PhoneNumberRule.clean(form.getPhoneNumberAlt()));
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(form.getRole());
        user.setStatus(form.getStatus());

        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, UserForm form) {
        PhoneNumberRule.requireAtLeastOne(form);
        User user = findById(id);
        user.setName(form.getName());
        user.setEmail(form.getEmail());
        user.setPhoneNumber(PhoneNumberRule.clean(form.getPhoneNumber()));
        user.setPhoneNumberAlt(PhoneNumberRule.clean(form.getPhoneNumberAlt()));
        user.setRole(form.getRole());
        user.setStatus(form.getStatus());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void updateStatus(Long id, UserStatus status) {
        User user = findById(id);
        user.setStatus(status);
        userRepository.save(user);
    }

    /** Customer self-service "my account" edit - name plus the basic contact fields. */
    @Transactional
    public User updateContactInfo(Long id, AccountForm form) {
        PhoneNumberRule.requireAtLeastOne(form);
        User user = findById(id);
        if (form.getName() != null && !form.getName().isBlank()) {
            user.setName(form.getName());
        }
        user.setPhoneNumber(PhoneNumberRule.clean(form.getPhoneNumber()));
        user.setPhoneNumberAlt(PhoneNumberRule.clean(form.getPhoneNumberAlt()));
        user.setAddress(form.getAddress());
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        try {
            userRepository.deleteById(id);
            userRepository.flush();   // force the DELETE now so a foreign-key violation surfaces here
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("This account can't be deleted because it has bookings, payments or other records attached. Suspend it instead.", ex);
        }
    }
}
