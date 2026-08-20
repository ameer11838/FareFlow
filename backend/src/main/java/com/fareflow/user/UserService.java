package com.fareflow.user;

import com.fareflow.exception.InvalidStateException;
import com.fareflow.exception.ResourceNotFoundException;
import com.fareflow.user.dto.CreateUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User create(CreateUserRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new InvalidStateException("A user with email %s already exists".formatted(email));
        }

        String timezone = request.timezoneOrDefault();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }

        return userRepository.save(request.weeklyBudgetCents() == null
                ? User.withoutBudget(request.name().trim(), email, timezone, null)
                : new User(request.name().trim(), email, request.weeklyBudgetCents(), timezone));
    }

    public User getById(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User %d was not found".formatted(userId)));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateWeeklyBudget(long userId, Long weeklyBudgetCents) {
        User user = getById(userId);
        user.setWeeklyBudgetCents(weeklyBudgetCents);
        return userRepository.save(user);
    }
}
