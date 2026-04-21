package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Maintenance;
import com.facility.booking.entity.User;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.MaintenanceRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.security.CurrentUserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceRepository maintenanceRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public MaintenanceController(
            MaintenanceRepository maintenanceRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.maintenanceRepository = maintenanceRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/list")
    public Result<List<Maintenance>> list() {
        List<Maintenance> maintenances = filterMaintenancesForCurrentMaintainer(maintenanceRepository.findAll());
        enrichMaintenances(maintenances);
        return Result.success(maintenances);
    }

    @GetMapping("/facility/{facilityId}")
    public Result<List<Maintenance>> getByfacilityId(@PathVariable Long facilityId) {
        if (!canCurrentMaintainerAccessFacility(facilityId)) {
            return Result.error(403, "无权查看该设施的维护记录");
        }

        List<Maintenance> maintenances = maintenanceRepository.findByFacilityId(facilityId);
        enrichMaintenances(maintenances);
        return Result.success(maintenances);
    }

    @GetMapping("/maintainer/{maintainerId}")
    public Result<List<Maintenance>> getByMaintainerId(@PathVariable Long maintainerId) {
        List<Maintenance> maintenances;
        if (currentUserService.hasRole("MAINTAINER")) {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (!Objects.equals(currentUserId, maintainerId)) {
                return Result.error(403, "无权查看其他设施管理员的维护记录");
            }
            maintenances = filterMaintenancesForCurrentMaintainer(maintenanceRepository.findAll());
        } else {
            maintenances = maintenanceRepository.findByMaintainerId(maintainerId);
        }

        enrichMaintenances(maintenances);
        return Result.success(maintenances);
    }

    @GetMapping("/{id}")
    public Result<Maintenance> getById(@PathVariable Long id) {
        Optional<Maintenance> maintenanceOpt = maintenanceRepository.findById(id);
        if (maintenanceOpt.isEmpty()) {
            return Result.error("维护记录不存在");
        }

        Maintenance maintenance = maintenanceOpt.get();
        if (!canCurrentMaintainerAccessFacility(maintenance.getFacilityId())) {
            return Result.error(403, "无权查看该维护记录");
        }

        enrichMaintenance(maintenance);
        return Result.success(maintenance);
    }

    @PostMapping
    @OperationLog(operationType = "CREATE_MAINTENANCE", detail = "创建维护任务")
    public Result<Maintenance> create(@RequestBody Maintenance maintenance) {
        if (maintenance.getFacilityId() == null) {
            return Result.error("设施ID不能为空");
        }
        if (!canCurrentMaintainerAccessFacility(maintenance.getFacilityId())) {
            return Result.error(403, "无权为该设施创建维护任务");
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(maintenance.getFacilityId());
        if (facilityOpt.isEmpty()) {
            return Result.error("设施不存在");
        }

        if (currentUserService.hasRole("MAINTAINER")) {
            maintenance.setMaintainerId(currentUserService.getCurrentUserId());
        }

        String validationError = validateMaintenance(maintenance);
        if (validationError != null) {
            return Result.error(validationError);
        }

        if (maintenance.getStatus() == null || maintenance.getStatus().isBlank()) {
            maintenance.setStatus("PENDING");
        }

        fillMaintainerName(maintenance);
        Maintenance savedMaintenance = maintenanceRepository.save(maintenance);
        syncFacilityStatus(savedMaintenance, null);
        enrichMaintenance(savedMaintenance);
        return Result.success("创建成功", savedMaintenance);
    }

    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_MAINTENANCE", detail = "更新维护任务")
    public Result<Maintenance> update(@PathVariable Long id, @RequestBody Maintenance maintenance) {
        Optional<Maintenance> existingOpt = maintenanceRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Result.error("维护记录不存在");
        }

        Maintenance existing = existingOpt.get();
        if (!canCurrentMaintainerAccessFacility(existing.getFacilityId())) {
            return Result.error(403, "无权编辑该维护记录");
        }

        Long targetFacilityId = maintenance.getFacilityId() != null ? maintenance.getFacilityId() : existing.getFacilityId();
        if (!canCurrentMaintainerAccessFacility(targetFacilityId)) {
            return Result.error(403, "无权将维护记录关联到该设施");
        }

        if (maintenance.getFacilityId() == null) {
            maintenance.setFacilityId(existing.getFacilityId());
        }
        if (maintenance.getMaintainerId() == null) {
            maintenance.setMaintainerId(existing.getMaintainerId());
        }
        if (currentUserService.hasRole("MAINTAINER")) {
            maintenance.setMaintainerId(currentUserService.getCurrentUserId());
        }
        if (maintenance.getMaintenanceType() == null) {
            maintenance.setMaintenanceType(existing.getMaintenanceType());
        }
        if (maintenance.getDescription() == null) {
            maintenance.setDescription(existing.getDescription());
        }
        if (maintenance.getMaintainer() == null) {
            maintenance.setMaintainer(existing.getMaintainer());
        }
        if (maintenance.getStatus() == null || maintenance.getStatus().isBlank()) {
            maintenance.setStatus(existing.getStatus());
        }
        if (maintenance.getStartTime() == null) {
            maintenance.setStartTime(existing.getStartTime());
        }
        if (maintenance.getEndTime() == null) {
            maintenance.setEndTime(existing.getEndTime());
        }
        if (maintenance.getResult() == null) {
            maintenance.setResult(existing.getResult());
        }
        if (maintenance.getCost() == null) {
            maintenance.setCost(existing.getCost());
        }

        String validationError = validateMaintenance(maintenance);
        if (validationError != null) {
            return Result.error(validationError);
        }

        maintenance.setId(id);
        fillMaintainerName(maintenance);

        String oldStatus = existing.getStatus();
        Maintenance savedMaintenance = maintenanceRepository.save(maintenance);
        syncFacilityStatus(savedMaintenance, oldStatus);
        enrichMaintenance(savedMaintenance);
        return Result.success("更新成功", savedMaintenance);
    }

    @PutMapping("/{id}/complete")
    @OperationLog(operationType = "COMPLETE_MAINTENANCE", detail = "完成维护")
    public Result<Maintenance> complete(@PathVariable Long id, @RequestBody Maintenance maintenance) {
        Optional<Maintenance> existingOpt = maintenanceRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return Result.error("维护记录不存在");
        }

        Maintenance existing = existingOpt.get();
        if (!canCurrentMaintainerAccessFacility(existing.getFacilityId())) {
            return Result.error(403, "无权完成该维护任务");
        }
        if ("COMPLETED".equals(existing.getStatus())) {
            return Result.error("维护任务已完成");
        }

        LocalDateTime endTime = maintenance.getEndTime() != null ? maintenance.getEndTime() : LocalDateTime.now();
        if (existing.getStartTime() != null && endTime.isBefore(existing.getStartTime())) {
            return Result.error("结束时间不能早于开始时间");
        }

        existing.setStatus("COMPLETED");
        existing.setEndTime(endTime);
        if (currentUserService.hasRole("MAINTAINER")) {
            existing.setMaintainerId(currentUserService.getCurrentUserId());
        }
        if (maintenance.getResult() != null) {
            existing.setResult(maintenance.getResult());
        }
        if (maintenance.getCost() != null) {
            existing.setCost(maintenance.getCost());
        }
        fillMaintainerName(existing);

        Maintenance savedMaintenance = maintenanceRepository.save(existing);
        restoreFacilityIfNeeded(existing.getFacilityId());
        enrichMaintenance(savedMaintenance);
        return Result.success("维护任务已完成", savedMaintenance);
    }

    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_MAINTENANCE", detail = "删除维护任务")
    public Result<Void> delete(@PathVariable Long id) {
        Optional<Maintenance> maintenanceOpt = maintenanceRepository.findById(id);
        if (maintenanceOpt.isEmpty()) {
            return Result.error("维护记录不存在");
        }

        Maintenance maintenance = maintenanceOpt.get();
        if (!canCurrentMaintainerAccessFacility(maintenance.getFacilityId())) {
            return Result.error(403, "无权删除该维护记录");
        }

        maintenanceRepository.deleteById(id);
        restoreFacilityIfNeeded(maintenance.getFacilityId());
        return Result.success("删除成功", null);
    }

    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(@RequestParam String range) {
        LocalDateTime startTime = getStartTimeByRange(range);
        List<Maintenance> maintenances = getScopedMaintenances(startTime);
        enrichMaintenances(maintenances);

        Map<String, Object> result = new HashMap<>();
        result.put("total", maintenances.size());
        result.put("maintenances", maintenances);
        return Result.success(result);
    }

    @GetMapping("/stats/type-distribution")
    public Result<Map<String, Object>> getTypeDistribution(@RequestParam(required = false) String range) {
        LocalDateTime startTime = range != null ? getStartTimeByRange(range) : null;
        List<Maintenance> maintenances = getScopedMaintenances(startTime);

        Map<String, Long> typeCount = new LinkedHashMap<>();
        typeCount.put("ROUTINE", 0L);
        typeCount.put("REPAIR", 0L);
        typeCount.put("UPGRADE", 0L);
        typeCount.put("OTHER", 0L);

        for (Maintenance maintenance : maintenances) {
            String normalizedType = normalizeMaintenanceType(maintenance.getMaintenanceType());
            typeCount.put(normalizedType, typeCount.getOrDefault(normalizedType, 0L) + 1);
        }

        String[] colors = {"#409eff", "#67c23a", "#e6a23c", "#909399"};
        String[] typeNames = {"日常保养", "故障维修", "设备升级", "其他"};
        List<Map<String, Object>> pieData = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Long> entry : typeCount.entrySet()) {
            if (entry.getValue() > 0) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", typeNames[index]);
                item.put("value", entry.getValue());
                item.put("itemStyle", Map.of("color", colors[index]));
                pieData.add(item);
            }
            index++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("typeDistribution", pieData);
        return Result.success(result);
    }

    @GetMapping("/stats/duration")
    public Result<Map<String, Object>> getDurationStats(@RequestParam(required = false) String range) {
        LocalDateTime startTime = range != null ? getStartTimeByRange(range) : null;
        List<Maintenance> maintenances = getScopedMaintenances(startTime).stream()
                .filter(item -> "COMPLETED".equals(item.getStatus()))
                .filter(item -> item.getStartTime() != null && item.getEndTime() != null)
                .collect(Collectors.toList());

        Map<String, List<Long>> durationBuckets = new LinkedHashMap<>();
        durationBuckets.put("ROUTINE", new ArrayList<>());
        durationBuckets.put("REPAIR", new ArrayList<>());
        durationBuckets.put("UPGRADE", new ArrayList<>());
        durationBuckets.put("OTHER", new ArrayList<>());

        for (Maintenance maintenance : maintenances) {
            String normalizedType = normalizeMaintenanceType(maintenance.getMaintenanceType());
            long hours = Duration.between(maintenance.getStartTime(), maintenance.getEndTime()).toHours();
            durationBuckets.get(normalizedType).add(hours);
        }

        String[] typeNames = {"日常保养", "故障维修", "设备升级", "其他"};
        String[] types = {"ROUTINE", "REPAIR", "UPGRADE", "OTHER"};
        List<Map<String, Object>> barData = new ArrayList<>();

        for (int i = 0; i < types.length; i++) {
            List<Long> durations = durationBuckets.get(types[i]);
            double avg = durations.isEmpty() ? 0D : durations.stream().mapToLong(Long::longValue).average().orElse(0D);
            BigDecimal rounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);

            Map<String, Object> item = new HashMap<>();
            item.put("type", typeNames[i]);
            item.put("avgDuration", rounded.doubleValue());
            barData.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("durationData", barData);
        return Result.success(result);
    }

    @GetMapping("/stats/facility-faults")
    public Result<Map<String, Object>> getFacilityFaultStats(@RequestParam(required = false) String range) {
        LocalDateTime startTime = range != null ? getStartTimeByRange(range) : null;
        List<Maintenance> maintenances = getScopedMaintenances(startTime);

        Map<Long, Long> faultCount = maintenances.stream()
                .filter(item -> item.getFacilityId() != null)
                .collect(Collectors.groupingBy(Maintenance::getFacilityId, Collectors.counting()));

        Map<Long, Facility> facilitiesById = facilityRepository.findAllById(faultCount.keySet())
                .stream()
                .collect(Collectors.toMap(Facility::getId, facility -> facility));

        List<Map<String, Object>> topFaults = faultCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("facilityId", entry.getKey());
                    item.put("faultCount", entry.getValue());
                    Facility facility = facilitiesById.get(entry.getKey());
                    item.put("facilityName", facility != null ? facility.getName() : "Unknown facility");
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("faultRanking", topFaults);
        return Result.success(result);
    }

    @GetMapping("/stats/summary")
    public Result<Map<String, Object>> getSummaryStats() {
        List<Maintenance> maintenances = getScopedMaintenances(null);
        List<Facility> facilities = getScopedFacilities();

        long total = maintenances.size();
        long pending = maintenances.stream().filter(item -> "PENDING".equals(item.getStatus())).count();
        long inProgress = maintenances.stream().filter(item -> "IN_PROGRESS".equals(item.getStatus())).count();
        long completed = maintenances.stream().filter(item -> "COMPLETED".equals(item.getStatus())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalFacilities", facilities.size());
        result.put("totalMaintenance", total);
        result.put("pendingMaintenance", pending);
        result.put("inProgressMaintenance", inProgress);
        result.put("completedMaintenance", completed);
        return Result.success(result);
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    public void checkPendingMaintenances() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fifteenMinutesLater = now.plusMinutes(15);
        List<Maintenance> pendingMaintenances = maintenanceRepository
                .findByStatusAndStartTimeLessThanEqualAndStartTimeAfter("PENDING", fifteenMinutesLater, now.minusMinutes(15));

        for (Maintenance maintenance : pendingMaintenances) {
            maintenance.setStatus("IN_PROGRESS");
            maintenanceRepository.save(maintenance);

            Optional<Facility> facilityOpt = facilityRepository.findById(maintenance.getFacilityId());
            if (facilityOpt.isPresent()) {
                Facility facility = facilityOpt.get();
                if (!"MAINTENANCE".equals(facility.getStatus())) {
                    facility.setStatus("MAINTENANCE");
                    facilityRepository.save(facility);
                }
            }
        }
    }

    private String validateMaintenance(Maintenance maintenance) {
        if (maintenance.getMaintainerId() == null) {
            return "维护人员ID不能为空";
        }
        if (maintenance.getMaintenanceType() == null || maintenance.getMaintenanceType().trim().isEmpty()) {
            return "维护类型不能为空";
        }
        if (maintenance.getDescription() == null || maintenance.getDescription().trim().isEmpty()) {
            return "维护描述不能为空";
        }
        if (maintenance.getStartTime() != null && maintenance.getEndTime() != null
                && maintenance.getEndTime().isBefore(maintenance.getStartTime())) {
            return "结束时间不能早于开始时间";
        }
        return null;
    }

    private List<Maintenance> getScopedMaintenances(LocalDateTime startTime) {
        List<Maintenance> maintenances = startTime == null
                ? maintenanceRepository.findAll()
                : maintenanceRepository.findByCreatedAtAfter(startTime);
        return filterMaintenancesForCurrentMaintainer(maintenances);
    }

    private List<Facility> getScopedFacilities() {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return facilityRepository.findAll();
        }

        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return new ArrayList<>();
        }
        return facilityRepository.findByMaintainerId(currentUserId);
    }

    private List<Maintenance> filterMaintenancesForCurrentMaintainer(List<Maintenance> maintenances) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return maintenances;
        }

        Set<Long> facilityIds = getScopedFacilities().stream()
                .map(Facility::getId)
                .collect(Collectors.toSet());

        return maintenances.stream()
                .filter(item -> item.getFacilityId() != null && facilityIds.contains(item.getFacilityId()))
                .collect(Collectors.toList());
    }

    private boolean canCurrentMaintainerAccessFacility(Long facilityId) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return true;
        }
        if (facilityId == null) {
            return false;
        }

        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(facilityId);
        return facilityOpt.isPresent() && Objects.equals(facilityOpt.get().getMaintainerId(), currentUserId);
    }

    private void enrichMaintenances(List<Maintenance> maintenances) {
        if (maintenances == null || maintenances.isEmpty()) {
            return;
        }

        Map<Long, Facility> facilitiesById = facilityRepository.findAllById(
                        maintenances.stream()
                                .map(Maintenance::getFacilityId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Facility::getId, facility -> facility));

        Map<Long, User> maintainersById = userRepository.findAllById(
                        maintenances.stream()
                                .map(Maintenance::getMaintainerId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        for (Maintenance maintenance : maintenances) {
            Facility facility = facilitiesById.get(maintenance.getFacilityId());
            if (facility != null) {
                maintenance.setFacilityName(facility.getName());
            }
            fillMaintainerName(maintenance, maintainersById);
        }
    }

    private void enrichMaintenance(Maintenance maintenance) {
        Optional<Facility> facilityOpt = facilityRepository.findById(maintenance.getFacilityId());
        facilityOpt.ifPresent(facility -> maintenance.setFacilityName(facility.getName()));
        fillMaintainerName(maintenance);
    }

    private void fillMaintainerName(Maintenance maintenance) {
        if (maintenance.getMaintainerId() == null) {
            return;
        }
        if (maintenance.getMaintainer() != null && !maintenance.getMaintainer().trim().isEmpty()) {
            return;
        }

        Optional<User> userOpt = userRepository.findById(maintenance.getMaintainerId());
        userOpt.ifPresent(user -> maintenance.setMaintainer(getDisplayName(user)));
    }

    private void fillMaintainerName(Maintenance maintenance, Map<Long, User> maintainersById) {
        if (maintenance.getMaintainerId() == null) {
            return;
        }
        if (maintenance.getMaintainer() != null && !maintenance.getMaintainer().trim().isEmpty()) {
            return;
        }

        User user = maintainersById.get(maintenance.getMaintainerId());
        if (user != null) {
            maintenance.setMaintainer(getDisplayName(user));
        }
    }

    private String getDisplayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    private String normalizeMaintenanceType(String maintenanceType) {
        if (maintenanceType == null || maintenanceType.isBlank()) {
            return "OTHER";
        }

        return switch (maintenanceType.trim().toUpperCase()) {
            case "ROUTINE", "日常保养" -> "ROUTINE";
            case "REPAIR", "故障维修" -> "REPAIR";
            case "UPGRADE", "设备升级" -> "UPGRADE";
            default -> "OTHER";
        };
    }

    private LocalDateTime getStartTimeByRange(String range) {
        LocalDateTime now = LocalDateTime.now();
        return switch (range) {
            case "1d" -> now.minusDays(1);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            case "180d" -> now.minusDays(180);
            case "365d" -> now.minusDays(365);
            default -> now.minusDays(7);
        };
    }

    private void restoreFacilityIfNeeded(Long facilityId) {
        if (facilityId == null) {
            return;
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(facilityId);
        if (facilityOpt.isEmpty()) {
            return;
        }

        boolean hasActiveMaintenance = maintenanceRepository.findByFacilityId(facilityId).stream()
                .anyMatch(item -> "PENDING".equals(item.getStatus()) || "IN_PROGRESS".equals(item.getStatus()));

        if (!hasActiveMaintenance) {
            Facility facility = facilityOpt.get();
            if ("MAINTENANCE".equals(facility.getStatus())) {
                facility.setStatus("AVAILABLE");
                facilityRepository.save(facility);
            }
        }
    }

    private void syncFacilityStatus(Maintenance maintenance, String oldStatus) {
        if (maintenance.getFacilityId() == null) {
            return;
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(maintenance.getFacilityId());
        if (facilityOpt.isEmpty()) {
            return;
        }

        Facility facility = facilityOpt.get();
        String newStatus = maintenance.getStatus();
        if ("IN_PROGRESS".equals(newStatus) && !"IN_PROGRESS".equals(oldStatus)) {
            facility.setStatus("MAINTENANCE");
            facilityRepository.save(facility);
            return;
        }

        if ("COMPLETED".equals(newStatus) && !"COMPLETED".equals(oldStatus)) {
            restoreFacilityIfNeeded(maintenance.getFacilityId());
            return;
        }

        if (maintenance.getStartTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime switchTime = maintenance.getStartTime().minusMinutes(15);
            if ((now.isAfter(switchTime) || now.isEqual(switchTime))
                    && !"COMPLETED".equals(newStatus)
                    && !"CANCELLED".equals(newStatus)) {
                if (!"MAINTENANCE".equals(facility.getStatus())) {
                    facility.setStatus("MAINTENANCE");
                    facilityRepository.save(facility);
                }
            }
        }
    }
}
