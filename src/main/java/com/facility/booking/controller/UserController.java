package com.facility.booking.controller;

import com.facility.booking.common.Result;
import com.facility.booking.dto.auth.AuthResponse;
import com.facility.booking.dto.user.AdminUserCreateRequest;
import com.facility.booking.dto.user.AdminUserUpdateRequest;
import com.facility.booking.dto.user.ChangePasswordRequest;
import com.facility.booking.dto.user.CurrentUserProfileUpdateRequest;
import com.facility.booking.dto.user.LoginRequest;
import com.facility.booking.dto.user.RegisterRequest;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.User;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.security.CustomUserPrincipal;
import com.facility.booking.security.JwtTokenProvider;
import com.facility.booking.service.ViolationRecordService;
import com.facility.booking.util.PageUtils;
import com.facility.booking.util.StoredFileUrlUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final ViolationRecordService violationRecordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;

    public UserController(
            UserRepository userRepository,
            ViolationRecordService violationRecordService,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            FacilityRepository facilityRepository,
            ReservationRepository reservationRepository
    ) {
        this.userRepository = userRepository;
        this.violationRecordService = violationRecordService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.facilityRepository = facilityRepository;
        this.reservationRepository = reservationRepository;
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Result.error("用户名或密码错误");
        }

        User user = userOpt.get();
        if (!isPasswordValid(request.getPassword(), user)) {
            return Result.error("用户名或密码错误");
        }

        upgradeLegacyPasswordIfNecessary(request.getPassword(), user);
        return Result.success("登录成功", buildAuthResponse(user));
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return Result.error("用户名已存在");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.findByEmail(request.getEmail()).isPresent()) {
            return Result.error("邮箱已被使用");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setEmail(blankToNull(request.getEmail()));
        user.setPhone(blankToNull(request.getPhone()));
        user.setRole("USER");
        user.setStatus("ACTIVE");

        User savedUser = userRepository.save(user);
        return Result.success("注册成功", buildAuthResponse(savedUser));
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            PageRequest pageRequest = PageRequest.of(
                    Math.max(page, 0),
                    PageUtils.normalizeSize(size),
                    Sort.by(Sort.Direction.DESC, "id")
            );
            Page<User> users = userRepository.findAll(pageRequest);
            return Result.success(users.map(this::toSafeUser));
        } catch (Exception e) {
            return Result.error("获取用户列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/reportable-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAINTAINER')")
    public Result<List<User>> getReportableUsers() {
        try {
            List<User> users;
            if (currentUserService.hasRole("MAINTAINER")) {
                users = getMaintainerReportableUsers(currentUserService.getCurrentUserId());
            } else {
                users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "realName"))
                        .stream()
                        .filter(this::isReportableRole)
                        .map(this::toReportableUser)
                        .toList();
            }
            return Result.success(users);
        } catch (Exception e) {
            return Result.error("获取可上报用户列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> getById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty()) {
            return Result.error("用户不存在");
        }

        return Result.success(toSafeUser(user.get()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> create(@Valid @RequestBody AdminUserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return Result.error("用户名已存在");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.findByEmail(request.getEmail()).isPresent()) {
            return Result.error("邮箱已被使用");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setRole(request.getRole());
        user.setPhone(blankToNull(request.getPhone()));
        user.setEmail(blankToNull(request.getEmail()));
        user.setAvatar(normalizeOptionalFileUrl(request.getAvatar()));
        user.setStatus(defaultIfBlank(request.getStatus(), "ACTIVE"));

        User savedUser = userRepository.save(user);
        return Result.success("创建成功", toSafeUser(savedUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return Result.error("用户不存在");
        }

        User existingUser = userOpt.get();
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Optional<User> existingEmailUser = userRepository.findByEmail(request.getEmail());
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(id)) {
                return Result.error("邮箱已被使用");
            }
        }

        existingUser.setRealName(request.getRealName().trim());
        existingUser.setPhone(blankToNull(request.getPhone()));
        existingUser.setEmail(blankToNull(request.getEmail()));
        existingUser.setRole(request.getRole());
        existingUser.setStatus(defaultIfBlank(request.getStatus(), "ACTIVE"));
        existingUser.setAvatar(normalizeOptionalFileUrl(request.getAvatar()));

        User savedUser = userRepository.save(existingUser);
        return Result.success("更新成功", toSafeUser(savedUser));
    }

    @PutMapping("/me")
    public Result<User> updateCurrentUser(@Valid @RequestBody CurrentUserProfileUpdateRequest request) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<User> userOpt = userRepository.findById(currentUserId);
        if (userOpt.isEmpty()) {
            return Result.error("用户不存在");
        }

        User existingUser = userOpt.get();
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Optional<User> existingEmailUser = userRepository.findByEmail(request.getEmail());
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(currentUserId)) {
                return Result.error("邮箱已被使用");
            }
        }

        existingUser.setRealName(request.getRealName().trim());
        existingUser.setPhone(blankToNull(request.getPhone()));
        existingUser.setEmail(blankToNull(request.getEmail()));
        existingUser.setAvatar(normalizeOptionalFileUrl(request.getAvatar()));

        User savedUser = userRepository.save(existingUser);
        return Result.success("更新成功", toSafeUser(savedUser));
    }

    @PostMapping("/{id}/change-password")
    public Result<String> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        if (!canAccessUser(id)) {
            return Result.error(403, "无权修改该用户密码");
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return Result.error("用户不存在");
        }

        User existingUser = userOpt.get();
        if (!isPasswordValid(request.getCurrentPassword(), existingUser)) {
            return Result.error("当前密码错误");
        }

        upgradeLegacyPasswordIfNecessary(request.getCurrentPassword(), existingUser);
        existingUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(existingUser);
        return Result.success("密码修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return Result.error("用户不存在");
        }
        userRepository.deleteById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> searchUsers(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<User> users = userRepository.findByRealNameLikeOrUsernameLike(keyword.trim())
                .stream()
                .map(this::toSafeUser)
                .toList();
        return Result.success(users);
    }

    @GetMapping("/maintainers")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> listMaintainers() {
        List<User> maintainers = userRepository.findByRoleOrderByRealNameAsc("MAINTAINER")
                .stream()
                .map(this::toSafeUser)
                .toList();
        return Result.success(maintainers);
    }

    private List<User> getMaintainerReportableUsers(Long maintainerId) {
        if (maintainerId == null) {
            return List.of();
        }

        List<Long> facilityIds = facilityRepository.findByMaintainerId(maintainerId)
                .stream()
                .map(Facility::getId)
                .filter(Objects::nonNull)
                .toList();
        if (facilityIds.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = reservationRepository.findByFacilityIdIn(facilityIds)
                .stream()
                .map(Reservation::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAllById(userIds)
                .stream()
                .filter(this::isReportableRole)
                .sorted(Comparator.comparing(this::resolveUserDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toReportableUser)
                .toList();
    }

    private boolean isReportableRole(User user) {
        return !"ADMIN".equals(user.getRole()) && !"MAINTAINER".equals(user.getRole());
    }

    private String resolveUserDisplayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername() == null ? "" : user.getUsername();
    }

    private User toSafeUser(User user) {
        User safeUser = new User();
        safeUser.setId(user.getId());
        safeUser.setUsername(user.getUsername());
        safeUser.setRealName(user.getRealName());
        safeUser.setRole(user.getRole());
        safeUser.setPhone(user.getPhone());
        safeUser.setEmail(user.getEmail());
        safeUser.setAvatar(user.getAvatar());
        safeUser.setStatus(user.getStatus());
        safeUser.setCreatedAt(user.getCreatedAt());
        safeUser.setUpdatedAt(user.getUpdatedAt());
        violationRecordService.recalculateUserCreditScoreAndViolationCount(user.getId());
        safeUser.setCreditScore(violationRecordService.getUserCurrentCreditScore(user.getId()));
        safeUser.setViolationCount(violationRecordService.getUserViolationCount(user.getId()));
        safeUser.setPassword("******");
        return safeUser;
    }

    private User toReportableUser(User user) {
        User reportableUser = new User();
        reportableUser.setId(user.getId());
        reportableUser.setUsername(user.getUsername());
        reportableUser.setRealName(user.getRealName());
        reportableUser.setRole(user.getRole());
        reportableUser.setStatus(user.getStatus());
        return reportableUser;
    }

    private boolean canAccessUser(Long userId) {
        CustomUserPrincipal currentUser = currentUserService.getCurrentUser();
        return currentUser != null && ("ADMIN".equals(currentUser.role()) || currentUser.id().equals(userId));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalizeOptionalFileUrl(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : StoredFileUrlUtils.normalizeForClient(normalized);
    }

    private boolean isPasswordValid(String rawPassword, User user) {
        String storedPassword = user.getPassword();
        if (rawPassword == null || storedPassword == null) {
            return false;
        }

        if (isEncodedPassword(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return storedPassword.equals(rawPassword);
    }

    private void upgradeLegacyPasswordIfNecessary(String rawPassword, User user) {
        if (user.getPassword() == null || isEncodedPassword(user.getPassword())) {
            return;
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private boolean isEncodedPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private AuthResponse buildAuthResponse(User user) {
        CustomUserPrincipal principal = new CustomUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                user.getStatus()
        );
        return new AuthResponse(
                jwtTokenProvider.generateToken(principal),
                jwtTokenProvider.getExpirationMillis(),
                new AuthResponse.UserProfile(
                        user.getId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getRole(),
                        user.getPhone(),
                        user.getEmail(),
                        user.getAvatar(),
                        user.getStatus()
                )
        );
    }
}
