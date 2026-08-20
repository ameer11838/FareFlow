package com.fareflow.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;

/**
 * A FareFlow user and their weekly transportation budget.
 *
 * <p>There is deliberately no collection of trips or ledger entries mapped here.
 * A {@code @OneToMany} to ledger entries is convenient at ten rows and a
 * production incident at ten thousand; children are always reached through
 * repository queries with explicit paging.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * The canonical weekly transportation budget, in integer cents.
     *
     * <p>Nullable, and the difference matters: {@code null} is "this rider has not
     * set a budget", {@code 0} is "this rider set a budget of zero". Only the
     * second is a budget, so only the second produces budget pressure, a
     * utilization percentage, or a remaining balance. The first makes the UI ask
     * for one instead of reporting $0.00 as though it were a fact.
     *
     * <p>This column is the single source of budget truth. The travel profile
     * built during onboarding deliberately has no budget column of its own.
     */
    @Column(name = "weekly_budget_cents")
    private Long weeklyBudgetCents;

    @Column(nullable = false)
    private String timezone;

    /**
     * BCrypt hash, or null for accounts that cannot authenticate at all — the
     * demo identity being the only such account today. Never a plaintext password.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String role = "USER";

    /** Marks the single account demo mode resolves to. Owned by the server. */
    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // required by JPA
    }

    public User(String name, String email, long weeklyBudgetCents, String timezone) {
        this(name, email, weeklyBudgetCents, timezone, null);
    }

    public User(String name, String email, long weeklyBudgetCents, String timezone, String passwordHash) {
        this.name = name;
        this.email = email;
        setWeeklyBudgetCents(weeklyBudgetCents);
        this.timezone = timezone;
        this.passwordHash = passwordHash;
    }

    /**
     * A user who has not set a budget yet.
     *
     * <p>A factory rather than a {@code Long} constructor overload: two
     * constructors distinguished only by boxing is the kind of signature that gets
     * called wrongly, and "no budget" deserves to be spelled out at the call site.
     */
    public static User withoutBudget(String name, String email, String timezone, String passwordHash) {
        User user = new User(name, email, 0, timezone, passwordHash);
        user.weeklyBudgetCents = null;
        return user;
    }

    /** @param weeklyBudgetCents null clears the budget; it does not set it to zero */
    public void setWeeklyBudgetCents(Long weeklyBudgetCents) {
        if (weeklyBudgetCents != null && weeklyBudgetCents < 0) {
            throw new IllegalArgumentException("Weekly budget must not be negative");
        }
        this.weeklyBudgetCents = weeklyBudgetCents;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    /** Null when no budget has been set. Callers must not coerce that to zero. */
    public Long getWeeklyBudgetCents() {
        return weeklyBudgetCents;
    }

    public boolean hasWeeklyBudget() {
        return weeklyBudgetCents != null;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isDemo() {
        return demo;
    }

    /** Whether this account is capable of logging in at all. */
    public boolean canAuthenticate() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
