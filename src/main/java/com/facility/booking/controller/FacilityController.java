package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.User;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.FileUploadService;
import com.facility.booking.util.PageUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/facility")
public class FacilityController {

    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final CurrentUserService currentUserService;

    public FacilityController(
            FacilityRepository facilityRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            FileUploadService fileUploadService,
            CurrentUserService currentUserService
    ) {
        this.facilityRepository = facilityRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.fileUploadService = fileUploadService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/list")
    public Result<List<Facility>> list() {
        List<Facility> facilities = filterFacilitiesForCurrentMaintainer(facilityRepository.findAll());
        enrichFacilities(facilities);
        return Result.success(facilities);
    }

    @GetMapping("/mine")
    public Result<List<Facility>> mine() {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null || !currentUserService.hasRole("MAINTAINER")) {
            return Result.error(403, "仅设施管理员可查看自己负责的设施");
        }

        List<Facility> facilities = facilityRepository.findByMaintainerId(currentUserId);
        enrichFacilities(facilities);
        return Result.success(facilities);
    }

    @GetMapping("/maintainer/{maintainerId}")
    public Result<List<Facility>> getByMaintainerId(@PathVariable Long maintainerId) {
        if (currentUserService.hasRole("MAINTAINER")) {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (!Objects.equals(currentUserId, maintainerId)) {
                return Result.error(403, "无权查看其他设施管理员负责的设施");
            }
        }

        List<Facility> facilities = facilityRepository.findByMaintainerId(maintainerId);
        enrichFacilities(facilities);
        return Result.success(facilities);
    }

    @GetMapping("/available")
    public Result<List<Facility>> getAvailable() {
        List<Facility> facilities = facilityRepository.findByStatus("AVAILABLE");
        enrichFacilities(facilities);
        return Result.success(facilities);
    }

    @GetMapping("/{id}")
    public Result<Facility> getById(@PathVariable Long id) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }
        if (!canCurrentMaintainerAccessFacility(facilityOpt.get())) {
            return Result.error(403, "无权查看该设施");
        }

        Facility facility = facilityOpt.get();
        enrichFacility(facility);
        return Result.success(facility);
    }

    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getFacilityDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int days
    ) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        if (!canCurrentMaintainerAccessFacility(facility)) {
            return Result.error(403, "无权查看该设施");
        }

        enrichFacility(facility);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(days);
        List<Reservation> reservations = reservationRepository.findByFacilityId(id);
        Map<Long, User> usersById = userRepository.findAllById(
                        reservations.stream()
                                .map(Reservation::getUserId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (Reservation reservation : reservations) {
            if (!reservation.getStartTime().isAfter(now) || !reservation.getStartTime().isBefore(endDate)) {
                continue;
            }
            if ("REJECTED".equals(reservation.getStatus()) || "CANCELLED".equals(reservation.getStatus())) {
                continue;
            }

            Map<String, Object> reservationInfo = new HashMap<>();
            reservationInfo.put("id", reservation.getId());
            reservationInfo.put("startTime", reservation.getStartTime());
            reservationInfo.put("endTime", reservation.getEndTime());
            reservationInfo.put("status", reservation.getStatus());
            reservationInfo.put("purpose", reservation.getPurpose());
            User user = usersById.get(reservation.getUserId());
            reservationInfo.put("userName", user != null ? getDisplayName(user) : "未知用户");
            timeline.add(reservationInfo);
        }

        timeline.sort(Comparator.comparing(item -> (LocalDateTime) item.get("startTime")));

        Map<String, Object> response = new HashMap<>();
        response.put("facility", facility);
        response.put("reservations", timeline);
        response.put("queryDays", days);
        return Result.success(response);
    }

    @GetMapping("/search")
    public Result<List<Facility>> search(@RequestParam String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Facility> facilities = filterFacilitiesForCurrentMaintainer(facilityRepository.findAll())
                .stream()
                .filter(facility -> matchesKeyword(facility, normalizedKeyword))
                .collect(Collectors.toList());
        enrichFacilities(facilities);
        return Result.success(facilities);
    }

    @GetMapping("/listPage")
    public Result<Map<String, Object>> listPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageUtils.of(page, size, Sort.by(direction, sortBy));

        Page<Facility> facilityPage;
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserService.hasRole("MAINTAINER") && currentUserId != null) {
            facilityPage = facilityRepository.findByMaintainerId(currentUserId, pageable);
        } else {
            facilityPage = facilityRepository.findAll(pageable);
        }

        List<Facility> facilities = facilityPage.getContent();
        enrichFacilities(facilities);
        return Result.success(toPageResult(facilityPage, facilities));
    }

    @GetMapping("/searchPage")
    public Result<Map<String, Object>> searchPage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageUtils.of(page, size, Sort.by(direction, sortBy));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        Page<Facility> facilityPage;
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserService.hasRole("MAINTAINER") && currentUserId != null) {
            facilityPage = facilityRepository.searchByKeywordAndMaintainerId(normalizedKeyword, currentUserId, pageable);
        } else {
            facilityPage = facilityRepository.searchByKeyword(normalizedKeyword, pageable);
        }

        List<Facility> facilities = facilityPage.getContent();
        enrichFacilities(facilities);
        return Result.success(toPageResult(facilityPage, facilities));
    }

    @PostMapping
    @OperationLog(operationType = "CREATE_FACILITY", detail = "创建设施")
    public Result<Facility> create(@RequestBody Facility facility) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可创建设施");
        }

        String validationError = validateMaintainerBinding(facility.getMaintainerId());
        if (validationError != null) {
            return Result.error(validationError);
        }

        if (facility.getImageUrl() == null || facility.getImageUrl().isBlank()) {
            facility.setImageUrl("/files/facility/default-facility.svg");
        }

        Facility savedFacility = facilityRepository.save(facility);
        enrichFacility(savedFacility);
        return Result.success("创建成功", savedFacility);
    }

    @PostMapping(consumes = "multipart/form-data")
    @OperationLog(operationType = "CREATE_FACILITY", detail = "创建设施")
    public Result<Facility> createWithImage(
            @RequestPart("facility") Facility facility,
            @RequestPart(value = "image", required = false) MultipartFile imageFile
    ) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可创建设施");
        }

        String validationError = validateMaintainerBinding(facility.getMaintainerId());
        if (validationError != null) {
            return Result.error(validationError);
        }

        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!fileUploadService.isValidImageFile(imageFile)) {
                    return Result.error("只能上传图片文件");
                }
                facility.setImageUrl(fileUploadService.uploadFile(imageFile, "facility"));
            } else {
                facility.setImageUrl("/files/facility/default-facility.svg");
            }

            Facility savedFacility = facilityRepository.save(facility);
            enrichFacility(savedFacility);
            return Result.success("创建成功", savedFacility);
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/image")
    @OperationLog(operationType = "UPLOAD_FACILITY_IMAGE", detail = "上传设施图片")
    public Result<Facility> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可上传设施图片");
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        try {
            if (!fileUploadService.isValidImageFile(file)) {
                return Result.error("只能上传图片文件");
            }

            Facility facility = facilityOpt.get();
            if (facility.getImageUrl() != null && !facility.getImageUrl().isBlank()) {
                fileUploadService.deleteFile(facility.getImageUrl());
            }

            facility.setImageUrl(fileUploadService.uploadFile(file, "facility"));
            Facility savedFacility = facilityRepository.save(facility);
            enrichFacility(savedFacility);
            return Result.success("图片上传成功", savedFacility);
        } catch (Exception e) {
            return Result.error("图片上传失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}/image")
    @OperationLog(operationType = "DELETE_FACILITY_IMAGE", detail = "删除设施图片")
    public Result<Facility> deleteImage(@PathVariable Long id) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可删除设施图片");
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        if (facility.getImageUrl() != null && !facility.getImageUrl().isBlank()) {
            fileUploadService.deleteFile(facility.getImageUrl());
            facility.setImageUrl(null);
        }

        Facility savedFacility = facilityRepository.save(facility);
        enrichFacility(savedFacility);
        return Result.success("图片删除成功", savedFacility);
    }

    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_FACILITY", detail = "更新设施")
    public Result<Facility> update(@PathVariable Long id, @RequestBody Facility facility) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可编辑设施");
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        String validationError = validateMaintainerBinding(facility.getMaintainerId());
        if (validationError != null) {
            return Result.error(validationError);
        }

        Facility existingFacility = facilityOpt.get();
        if (facility.getName() != null) {
            existingFacility.setName(facility.getName());
        }
        if (facility.getModel() != null) {
            existingFacility.setModel(facility.getModel());
        }
        if (facility.getCategory() != null) {
            existingFacility.setCategory(facility.getCategory());
        }
        if (facility.getLocation() != null) {
            existingFacility.setLocation(facility.getLocation());
        }
        if (facility.getMaintainerId() != null || existingFacility.getMaintainerId() != null) {
            existingFacility.setMaintainerId(facility.getMaintainerId());
        }
        if (facility.getStatus() != null) {
            existingFacility.setStatus(facility.getStatus());
        }
        if (facility.getDescription() != null) {
            existingFacility.setDescription(facility.getDescription());
        }
        if (facility.getPurchaseDate() != null) {
            existingFacility.setPurchaseDate(facility.getPurchaseDate());
        }
        if (facility.getPrice() != null) {
            existingFacility.setPrice(facility.getPrice());
        }
        if (facility.getImageUrl() != null && !facility.getImageUrl().isBlank()) {
            existingFacility.setImageUrl(facility.getImageUrl());
        }

        Facility savedFacility = facilityRepository.save(existingFacility);
        enrichFacility(savedFacility);
        return Result.success("更新成功", savedFacility);
    }

    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE_FACILITY_STATUS", detail = "更新设施状态")
    public Result<Facility> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        if (currentUserService.hasRole("MAINTAINER") && !canCurrentMaintainerAccessFacility(facility)) {
            return Result.error(403, "无权更新该设施状态");
        }
        if (!currentUserService.hasRole("ADMIN") && !currentUserService.hasRole("MAINTAINER")) {
            return Result.error(403, "无权更新设施状态");
        }

        String status = requestBody.get("status");
        if (status == null || status.trim().isEmpty()) {
            return Result.error("状态不能为空");
        }

        facility.setStatus(status.trim().toUpperCase());
        Facility savedFacility = facilityRepository.save(facility);
        enrichFacility(savedFacility);
        return Result.success("设施状态更新成功", savedFacility);
    }

    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_FACILITY", detail = "删除设施")
    public Result<Void> delete(@PathVariable Long id) {
        if (!currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "仅系统管理员可删除设施");
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        if (facility.getImageUrl() != null && !facility.getImageUrl().isBlank()) {
            fileUploadService.deleteFile(facility.getImageUrl());
        }

        facilityRepository.deleteById(id);
        return Result.success("删除成功", null);
    }

    private Map<String, Object> toPageResult(Page<Facility> facilityPage, List<Facility> facilities) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", facilities);
        response.put("totalElements", facilityPage.getTotalElements());
        response.put("totalPages", facilityPage.getTotalPages());
        response.put("size", facilityPage.getSize());
        response.put("number", facilityPage.getNumber());
        response.put("first", facilityPage.isFirst());
        response.put("last", facilityPage.isLast());
        return response;
    }

    private List<Facility> filterFacilitiesForCurrentMaintainer(List<Facility> facilities) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return facilities;
        }

        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return new ArrayList<>();
        }

        return facilities.stream()
                .filter(facility -> Objects.equals(facility.getMaintainerId(), currentUserId))
                .collect(Collectors.toList());
    }

    private boolean canCurrentMaintainerAccessFacility(Facility facility) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return true;
        }
        Long currentUserId = currentUserService.getCurrentUserId();
        return currentUserId != null && Objects.equals(currentUserId, facility.getMaintainerId());
    }

    private String validateMaintainerBinding(Long maintainerId) {
        if (maintainerId == null) {
            return null;
        }

        Optional<User> userOpt = userRepository.findById(maintainerId);
        if (userOpt.isEmpty()) {
            return "所选设施负责人不存在";
        }
        if (!"MAINTAINER".equals(userOpt.get().getRole())) {
            return "设施负责人必须是设施管理员角色";
        }
        return null;
    }

    private boolean matchesKeyword(Facility facility, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalized = keyword.toLowerCase();
        return containsIgnoreCase(facility.getName(), normalized)
                || containsIgnoreCase(facility.getModel(), normalized)
                || containsIgnoreCase(facility.getCategory(), normalized)
                || containsIgnoreCase(facility.getLocation(), normalized);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void enrichFacilities(List<Facility> facilities) {
        if (facilities == null || facilities.isEmpty()) {
            return;
        }

        Set<Long> maintainerIds = facilities.stream()
                .map(Facility::getMaintainerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, User> usersById = userRepository.findAllById(maintainerIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        for (Facility facility : facilities) {
            fillMaintainerName(facility, usersById);
        }
    }

    private void enrichFacility(Facility facility) {
        if (facility == null || facility.getMaintainerId() == null) {
            return;
        }

        Optional<User> userOpt = userRepository.findById(facility.getMaintainerId());
        userOpt.ifPresent(user -> facility.setMaintainerName(getDisplayName(user)));
    }

    private void fillMaintainerName(Facility facility, Map<Long, User> usersById) {
        if (facility.getMaintainerId() == null) {
            facility.setMaintainerName(null);
            return;
        }

        User user = usersById.get(facility.getMaintainerId());
        if (user != null) {
            facility.setMaintainerName(getDisplayName(user));
        }
    }

    private String getDisplayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }
}
