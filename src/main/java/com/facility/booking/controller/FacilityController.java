package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.service.FileUploadService;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备控制器
 * 提供设备管理的相关接口，包括设备的增删改查、状态管理等功能
 */
@RestController
@RequestMapping("/api/facility")
public class FacilityController {

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 获取所有设备列表
     * @return 设备列表
     */
    @GetMapping("/list")
    public Result<List<Facility>> list() {
        List<Facility> facilities = facilityRepository.findAll();
        return Result.success(facilities);
    }

    /**
     * 获取所有可用设备
     * @return 可用设备列表
     */
    @GetMapping("/available")
    public Result<List<Facility>> getAvailable() {
        List<Facility> facilities = facilityRepository.findByStatus("AVAILABLE");
        return Result.success(facilities);
    }

    /**
     * 根据ID获取设备详情
     * @param id 设备ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    public Result<Facility> getById(@PathVariable Long id) {
        Optional<Facility> facility = facilityRepository.findById(id);
        if (facility.isPresent()) {
            return Result.success(facility.get());
        }
        return Result.error("设备不存在");
    }

    /**
     * 获取设施详情及未来占用情况
     * @param id 设施ID
     * @param days 展望天数，默认7天
     * @return 设施详情和未来占用时间轴
     */
    @GetMapping("/{id}/detail")
    public Result<Map<String, Object>> getFacilityDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "7") int days) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(days);

        List<Reservation> reservations = reservationRepository.findByFacilityId(id);
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (Reservation reservation : reservations) {
            if (reservation.getStartTime().isAfter(now) && reservation.getStartTime().isBefore(endDate)) {
                if (!"REJECTED".equals(reservation.getStatus()) && !"CANCELLED".equals(reservation.getStatus())) {
                    Map<String, Object> reservationInfo = new HashMap<>();
                    reservationInfo.put("id", reservation.getId());
                    reservationInfo.put("startTime", reservation.getStartTime());
                    reservationInfo.put("endTime", reservation.getEndTime());
                    reservationInfo.put("status", reservation.getStatus());
                    reservationInfo.put("purpose", reservation.getPurpose());
                    reservationInfo.put("userName", reservation.getUserName());
                    timeline.add(reservationInfo);
                }
            }
        }

        timeline.sort((a, b) -> {
            LocalDateTime startA = (LocalDateTime) a.get("startTime");
            LocalDateTime startB = (LocalDateTime) b.get("startTime");
            return startA.compareTo(startB);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("facility", facility);
        response.put("reservations", timeline);
        response.put("queryDays", days);

        return Result.success(response);
    }

    /**
     * 搜索设备（根据设备名称）
     * @param keyword 搜索关键词
     * @return 匹配的设备列表
     */
    @GetMapping("/search")
    public Result<List<Facility>> search(@RequestParam String keyword) {
        List<Facility> facilities = facilityRepository.findByNameContaining(keyword);
        return Result.success(facilities);
    }

    /**
     * 分页获取设备列表
     * @param page 页码，从0开始
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param sortDir 排序方向（asc/desc）
     * @return 分页数据
     */
    @GetMapping("/listPage")
    public Result<Map<String, Object>> listPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageUtils.of(page, size, Sort.by(direction, sortBy));
        Page<Facility> facilityPage = facilityRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", facilityPage.getContent());
        response.put("totalElements", facilityPage.getTotalElements());
        response.put("totalPages", facilityPage.getTotalPages());
        response.put("size", facilityPage.getSize());
        response.put("number", facilityPage.getNumber());
        response.put("first", facilityPage.isFirst());
        response.put("last", facilityPage.isLast());

        return Result.success(response);
    }

    /**
     * 分页搜索设备
     * @param keyword 搜索关键词
     * @param page 页码，从0开始
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param sortDir 排序方向（asc/desc）
     * @return 搜索结果的分页数据
     */
    @GetMapping("/searchPage")
    public Result<Map<String, Object>> searchPage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageUtils.of(page, size, Sort.by(direction, sortBy));
        Page<Facility> facilityPage = facilityRepository.searchByKeyword(keyword, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", facilityPage.getContent());
        response.put("totalElements", facilityPage.getTotalElements());
        response.put("totalPages", facilityPage.getTotalPages());
        response.put("size", facilityPage.getSize());
        response.put("number", facilityPage.getNumber());
        response.put("first", facilityPage.isFirst());
        response.put("last", facilityPage.isLast());

        return Result.success(response);
    }

    /**
     * 创建新设备
     * @param facility 设备信息
     * @return 创建的设备信息
     */
    @PostMapping
    @OperationLog(operationType = "CREATE_FACILITY", detail = "创建设施")
    public Result<Facility> create(@RequestBody Facility facility) {
        if (facility.getImageUrl() == null || facility.getImageUrl().isEmpty()) {
            facility.setImageUrl("/files/facility/default-facility.svg");
        }
        Facility savedfacility = facilityRepository.save(facility);
        return Result.success("创建成功", savedfacility);
    }

    /**
     * 创建新设备并上传图片
     * @param facility 设备信息
     * @param imageFile 图片文件
     * @return 创建的设备信息
     */
    @PostMapping(consumes = "multipart/form-data")
    @OperationLog(operationType = "CREATE_FACILITY", detail = "创建设施")
    public Result<Facility> createWithImage(
            @RequestPart("facility") Facility facility,
            @RequestPart(value = "image", required = false) MultipartFile imageFile) {
        try {
            // 如果提供了图片，先上传图片
            if (imageFile != null && !imageFile.isEmpty()) {
                if (!fileUploadService.isValidImageFile(imageFile)) {
                    return Result.error("只能上传图片文件");
                }
                String imageUrl = fileUploadService.uploadFile(imageFile, "facility");
                facility.setImageUrl(imageUrl);
            } else {
                // 如果没有上传图片，设置默认图片路径
                facility.setImageUrl("/files/facility/default-facility.svg");
            }
            
            Facility savedfacility = facilityRepository.save(facility);
            return Result.success("创建成功", savedfacility);
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 上传设施图片
     * @param id 设施ID
     * @param file 图片文件
     * @return 更新后的设施信息
     */
    @PostMapping("/{id}/image")
    @OperationLog(operationType = "UPLOAD_FACILITY_IMAGE", detail = "上传设施图片")
    public Result<Facility> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设施不存在");
        }

        try {
            // 验证文件类型
            if (!fileUploadService.isValidImageFile(file)) {
                return Result.error("只能上传图片文件");
            }

            Facility facility = facilityOpt.get();
            
            // 如果已有图片，先删除旧图片
            if (facility.getImageUrl() != null && !facility.getImageUrl().isEmpty()) {
                fileUploadService.deleteFile(facility.getImageUrl());
            }

            // 上传新图片到facility子目录
            String imageUrl = fileUploadService.uploadFile(file, "facility");
            facility.setImageUrl(imageUrl);
            
            Facility savedFacility = facilityRepository.save(facility);
            return Result.success("图片上传成功", savedFacility);
            
        } catch (Exception e) {
            return Result.error("图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除设施图片
     * @param id 设施ID
     * @return 更新后的设施信息
     */
    @DeleteMapping("/{id}/image")
    @OperationLog(operationType = "DELETE_FACILITY_IMAGE", detail = "删除设施图片")
    public Result<Facility> deleteImage(@PathVariable Long id) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设施不存在");
        }

        Facility facility = facilityOpt.get();
        
        // 删除图片文件
        if (facility.getImageUrl() != null && !facility.getImageUrl().isEmpty()) {
            fileUploadService.deleteFile(facility.getImageUrl());
            facility.setImageUrl(null);
            Facility savedFacility = facilityRepository.save(facility);
            return Result.success("图片删除成功", savedFacility);
        }

        return Result.success("设施没有图片", facility);
    }

    /**
     * 更新设备信息
     * @param id 设备ID
     * @param facility 更新的设备信息
     * @return 更新后的设备信息
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_FACILITY", detail = "更新设施")
    public Result<Facility> update(@PathVariable Long id, @RequestBody Facility facility) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设备不存在");
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
        if (facility.getImageUrl() != null && !facility.getImageUrl().isEmpty()) {
            existingFacility.setImageUrl(facility.getImageUrl());
        }
        
        Facility savedfacility = facilityRepository.save(existingFacility);
        return Result.success("更新成功", savedfacility);
    }

    /**
     * 更新设备状态
     * @param id 设备ID
     * @param requestBody 包含新状态的请求体
     * @return 更新后的设备信息
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE_FACILITY_STATUS", detail = "更新设施状态")
    public Result<Facility> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设备不存在");
        }

        Facility facility = facilityOpt.get();
        String status = requestBody.get("status");
        if (status == null || status.trim().isEmpty()) {
            return Result.error("状态不能为空");
        }

        facility.setStatus(status.trim().toUpperCase());
        
        Facility savedfacility = facilityRepository.save(facility);
        return Result.success("设备状态更新成功", savedfacility);
    }

    /**
     * 删除设备
     * @param id 设备ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_FACILITY", detail = "删除设施")
    public Result<Void> delete(@PathVariable Long id) {
        Optional<Facility> facilityOpt = facilityRepository.findById(id);
        if (!facilityOpt.isPresent()) {
            return Result.error("设备不存在");
        }

        Facility facility = facilityOpt.get();
        
        // 删除相关图片文件
        if (facility.getImageUrl() != null && !facility.getImageUrl().isEmpty()) {
            fileUploadService.deleteFile(facility.getImageUrl());
        }

        facilityRepository.deleteById(id);
        return Result.success("删除成功", null);
    }
}
