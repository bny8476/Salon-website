package com.luxesuite.api.service;

import com.luxesuite.api.dto.AppointmentDto;
import com.luxesuite.api.dto.AppointmentItemDto;
import com.luxesuite.api.model.*;
import com.luxesuite.api.repository.*;
import com.luxesuite.api.exception.ResourceNotFoundException;
import com.luxesuite.api.exception.BadRequestException;
import com.luxesuite.api.exception.ConflictException;
import com.luxesuite.api.security.SecurityUtils;
import com.luxesuite.api.scheduler.NotificationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentItemRepository appointmentItemRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final InventoryService inventoryService;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;
    private final NotificationScheduler notificationScheduler;
    private final SseService sseService;
    private final EmailService emailService;

    @Transactional
    public AppointmentDto createAppointment(AppointmentDto dto) {
        Customer customer = null;
        if (dto.getCustomerId() != null) {
            customer = customerRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            securityUtils.validateCustomerOwnership(customer.getUser() != null ? customer.getUser().getId() : null);
        } else {
            // Guest Flow
            if (dto.getGuestFirstName() == null || dto.getGuestEmail() == null) {
                throw new BadRequestException("Guest details are required if not logged in");
            }
            
            // Look up by email first, or create new guest customer
            java.util.Optional<Customer> existing = customerRepository.findByEmail(dto.getGuestEmail());
            if (existing.isPresent()) {
                customer = existing.get();
            } else {
                customer = new Customer();
                customer.setFirstName(dto.getGuestFirstName());
                customer.setLastName(dto.getGuestLastName());
                customer.setEmail(dto.getGuestEmail());
                customer.setPhone(dto.getGuestPhone());
                customer.setTotalPoints(0);
                customer = customerRepository.save(customer);
            }
        }
        Branch branch = branchRepository.findById(dto.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setBranch(branch);
        appointment.setNotes(dto.getNotes());
        
        BigDecimal totalPrice = BigDecimal.ZERO;

        if (dto.getServices() == null || dto.getServices().isEmpty()) {
            throw new BadRequestException("No services selected");
        }
        
        // Lock staff upfront in deterministic order to prevent deadlocks
        List<Long> distinctStaffIds = dto.getServices().stream()
                .map(com.luxesuite.api.dto.AppointmentItemDto::getStaffId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
                
        java.util.Map<Long, Staff> lockedStaffMap = new java.util.HashMap<>();
        for (Long staffId : distinctStaffIds) {
            Staff staff = staffRepository.findByIdForUpdate(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));
            lockedStaffMap.put(staffId, staff);
        }

        LocalDateTime currentStartTime = dto.getServices().get(0).getStartTime();
        boolean hasSpa = false;
        boolean hasSalon = false;

        for (AppointmentItemDto itemDto : dto.getServices()) {
            com.luxesuite.api.model.Service service = serviceRepository.findById(itemDto.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            
            if ("SPA".equals(service.getBusinessType())) hasSpa = true;
            if ("SALON".equals(service.getBusinessType())) hasSalon = true;
            
            Staff staff = lockedStaffMap.get(itemDto.getStaffId());
            if (staff == null) {
                throw new ResourceNotFoundException("Staff not found");
            }

            // Check if staff can perform this service
            if (!staff.getServices().contains(service)) {
                throw new BadRequestException("Staff " + staff.getUser().getFirstName() + " cannot perform service " + service.getName());
            }

            LocalDateTime endTime = currentStartTime.plusMinutes(service.getDurationMins());

            // Conflict detection engine (against existing DB records)
            List<AppointmentItem> conflicts = appointmentItemRepository.findOverlappingAppointments(
                    staff.getId(), currentStartTime, endTime);
            
            if (!conflicts.isEmpty()) {
                throw new ConflictException("Double booking detected for staff " + staff.getUser().getFirstName() + " at " + currentStartTime);
            }

            AppointmentItem item = new AppointmentItem();
            item.setAppointment(appointment);
            item.setService(service);
            item.setStaff(staff);
            item.setStartTime(currentStartTime);
            item.setEndTime(endTime);
            item.setPrice(service.getPrice()); // Snapshot the price

            totalPrice = totalPrice.add(service.getPrice());
            appointment.getServices().add(item);
            
            currentStartTime = endTime; // Sequence next service
        }
        appointment.setTotalPrice(totalPrice);
        
        // Calculate 20% deposit
        BigDecimal deposit = totalPrice.multiply(new BigDecimal("0.20"));
        appointment.setDepositAmount(deposit);
        appointment.setIsDepositPaid(false);
        // We leave the status as PENDING or BOOKED. If we had a PENDING_DEPOSIT status we would set it here.
        // For now, we will leave it as BOOKED, or we can use a new status.
        
        if (hasSpa && hasSalon) {
            appointment.setBusinessType("BOTH");
        } else if (hasSpa) {
            appointment.setBusinessType("SPA");
        } else if (hasSalon) {
            appointment.setBusinessType("SALON");
        } else {
            appointment.setBusinessType("BOTH");
        }
        
        Appointment savedAppointment = appointmentRepository.save(appointment);
        
        // Emit SSE event
        sseService.sendEventToAll("appointment_booked", savedAppointment.getId());
        
        // Send confirmation email
        if (customer.getUser() != null && customer.getUser().getEmail() != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
            String formattedTime = dto.getServices().get(0).getStartTime().format(formatter);
            String emailBody = String.format(
                "Dear %s,\n\nYour sanctuary awaits. Your appointment at Lumina Spa has been successfully booked.\n\n" +
                "When: %s\n" +
                "Total Amount: $%.2f\n\n" +
                "We look forward to guiding you through a moment of complete serenity.\n\n" +
                "Warm regards,\nThe Lumina Spa Team",
                customer.getUser().getFirstName(),
                formattedTime,
                totalPrice
            );
            emailService.sendEmail(customer.getUser().getEmail(), "Your Lumina Spa Booking Confirmation", emailBody);
        }
        
        return mapToDto(savedAppointment);
    }

    @Transactional(readOnly = true)
    public com.luxesuite.api.dto.PageResponse<AppointmentDto> getAppointmentsByBranchAndDateRange(Long branchId, LocalDateTime start, LocalDateTime end, int page, int size, String businessType) {
        securityUtils.validateBranchAccess(branchId);
        String bType = businessType != null ? businessType : "BOTH";
        List<String> validTypes = "BOTH".equals(bType) ? java.util.Arrays.asList("SPA", "SALON", "BOTH") : java.util.Arrays.asList("BOTH", bType);
        org.springframework.data.domain.Page<Appointment> appointments = appointmentRepository.findByBranchIdAndCreatedAtBetweenAndBusinessTypeIn(branchId, start, end, validTypes, org.springframework.data.domain.PageRequest.of(page, size));
        
        if (appointments.hasContent()) {
            List<Long> appointmentIds = appointments.stream().map(Appointment::getId).collect(Collectors.toList());
            // Pre-fetch items with service and staff to avoid N+1 during mapToDto
            List<AppointmentItem> items = appointmentItemRepository.findByAppointmentIdIn(appointmentIds);
            
            // Group the pre-fetched items back into their respective appointments
            java.util.Map<Long, List<AppointmentItem>> itemsByAppointment = items.stream()
                .collect(Collectors.groupingBy(item -> item.getAppointment().getId()));
                
            appointments.forEach(app -> {
                app.setServices(itemsByAppointment.getOrDefault(app.getId(), new java.util.ArrayList<>()));
            });
        }
        
        return com.luxesuite.api.dto.PageResponse.of(appointments.map(this::mapToDto));
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getAppointmentsByStaffAndDate(Long staffId, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        // Here we could validate staff access if needed
        List<Appointment> appointments = appointmentRepository.findAppointmentsByStaffAndDate(staffId, startOfDay, endOfDay);
        return appointments.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentDto getMyUpcomingAppointment() {
        com.luxesuite.api.model.User user = securityUtils.getCurrentUser();
        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        
        List<Appointment> upcoming = appointmentRepository.findByCustomerId(customer.getId()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED 
                        && a.getServices() != null && !a.getServices().isEmpty() 
                        && a.getServices().get(0).getStartTime().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(a -> a.getServices().get(0).getStartTime()))
                .collect(Collectors.toList());
                
        if (upcoming.isEmpty()) {
            return null; // Or throw exception, but frontend should handle null
        }
        return mapToDto(upcoming.get(0));
    }

    @Transactional
    public AppointmentDto completeAppointment(Long appointmentId, List<com.luxesuite.api.dto.ProductUsageDto> usedProducts) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        
        appointment.setStatus(AppointmentStatus.COMPLETED);
        
        for (AppointmentItem item : appointment.getServices()) {
            item.setStatus(AppointmentStatus.COMPLETED);
        }
        
        if (usedProducts != null) {
            for (com.luxesuite.api.dto.ProductUsageDto usage : usedProducts) {
                // Deduct stock (negative quantity adjustment)
                inventoryService.updateStock(usage.getProductId(), appointment.getBranch().getId(), -usage.getQuantity());
            }
        }
        
        Appointment savedAppointment = appointmentRepository.save(appointment);
        
        // Emit SSE event
        sseService.sendEventToAll("appointment_updated", savedAppointment.getId());
        
        return mapToDto(savedAppointment);
    }

    @Transactional
    public AppointmentDto rescheduleAppointment(Long appointmentId, LocalDateTime newStartTime) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        securityUtils.validateCustomerOwnership(appointment.getCustomer().getUser() != null ? appointment.getCustomer().getUser().getId() : null);

        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Cannot reschedule a completed or cancelled appointment.");
        }

        if (appointment.getServices().isEmpty()) {
            throw new BadRequestException("Appointment has no services.");
        }

        LocalDateTime currentOriginalStartTime = appointment.getServices().get(0).getStartTime();
        if (LocalDateTime.now().plusHours(24).isAfter(currentOriginalStartTime)) {
            throw new BadRequestException("Appointments must be rescheduled at least 24 hours in advance.");
        }
        
        // Lock staff upfront in deterministic order to prevent deadlocks
        List<Long> distinctStaffIds = appointment.getServices().stream()
                .map(item -> item.getStaff().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
                
        for (Long staffId : distinctStaffIds) {
            staffRepository.findByIdForUpdate(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));
        }

        LocalDateTime currentStartTime = newStartTime;
        for (AppointmentItem item : appointment.getServices()) {
            LocalDateTime endTime = currentStartTime.plusMinutes(item.getService().getDurationMins());
            
            // Check conflicts (exclude the current appointment items)
            List<AppointmentItem> conflicts = appointmentItemRepository.findOverlappingAppointments(
                    item.getStaff().getId(), currentStartTime, endTime);
            
            boolean hasRealConflict = conflicts.stream()
                    .anyMatch(c -> !c.getAppointment().getId().equals(appointmentId));
                    
            if (hasRealConflict) {
                throw new ConflictException("Double booking detected for staff " + item.getStaff().getUser().getFirstName() + " at " + currentStartTime);
            }

            item.setStartTime(currentStartTime);
            item.setEndTime(endTime);
            currentStartTime = endTime;
        }

        appointment.setReminder24hSent(false);
        appointment.setReminder2hSent(false);
        appointment.setReminderSentAt(null);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        sseService.sendEventToAll("appointment_updated", savedAppointment.getId());
        
        // Send email
        if (appointment.getCustomer().getUser() != null && appointment.getCustomer().getUser().getEmail() != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
            String formattedTime = appointment.getServices().get(0).getStartTime().format(formatter);
            String emailBody = String.format(
                "Dear %s,\n\nYour appointment at Lumina Spa has been successfully rescheduled.\n\n" +
                "New Time: %s\n\n" +
                "We look forward to guiding you through a moment of complete serenity.\n\n" +
                "Warm regards,\nThe Lumina Spa Team",
                appointment.getCustomer().getUser().getFirstName(),
                formattedTime
            );
            emailService.sendEmail(appointment.getCustomer().getUser().getEmail(), "Your Lumina Spa Appointment Rescheduled", emailBody);
        }

        return mapToDto(savedAppointment);
    }

    @Transactional
    public AppointmentDto updateStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Cannot change status of a completed or cancelled appointment.");
        }

        appointment.setStatus(newStatus);
        for (AppointmentItem item : appointment.getServices()) {
            item.setStatus(newStatus);
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);
        sseService.sendEventToAll("appointment_updated", savedAppointment.getId());
        return mapToDto(savedAppointment);
    }

    private AppointmentDto mapToDto(Appointment appointment) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(appointment.getId());
        dto.setCustomerId(appointment.getCustomer() != null ? appointment.getCustomer().getId() : null);
        
        if (appointment.getCustomer() != null) {
            dto.setCustomerFirstName(appointment.getCustomer().getFirstName());
            dto.setCustomerLastName(appointment.getCustomer().getLastName());
        } else if (appointment.getNotes() != null && appointment.getNotes().contains("Guest:")) {
             // Fallback for guest if not linked properly
             dto.setCustomerFirstName("Guest");
        }
        
        dto.setBranchId(appointment.getBranch() != null ? appointment.getBranch().getId() : null);
        dto.setStatus(appointment.getStatus());
        dto.setTotalPrice(appointment.getTotalPrice());
        dto.setNotes(appointment.getNotes());
        dto.setCreatedAt(appointment.getCreatedAt());
        dto.setBusinessType(appointment.getBusinessType());
        dto.setDepositAmount(appointment.getDepositAmount());
        dto.setIsDepositPaid(appointment.getIsDepositPaid());
        
        List<AppointmentItemDto> itemDtos = appointment.getServices().stream().map(item -> {
            AppointmentItemDto itemDto = new AppointmentItemDto();
            itemDto.setId(item.getId());
            itemDto.setServiceId(item.getService() != null ? item.getService().getId() : null);
            itemDto.setServiceName(item.getService() != null ? item.getService().getName() : null);
            itemDto.setStaffId(item.getStaff() != null ? item.getStaff().getId() : null);
            if (item.getStaff() != null && item.getStaff().getUser() != null) {
                itemDto.setStaffFirstName(item.getStaff().getUser().getFirstName());
                itemDto.setStaffLastName(item.getStaff().getUser().getLastName());
            }
            itemDto.setStartTime(item.getStartTime());
            itemDto.setEndTime(item.getEndTime());
            itemDto.setStatus(item.getStatus());
            itemDto.setPrice(item.getPrice());
            return itemDto;
        }).collect(Collectors.toList());
        
        dto.setServices(itemDtos);
        return dto;
    }
}
