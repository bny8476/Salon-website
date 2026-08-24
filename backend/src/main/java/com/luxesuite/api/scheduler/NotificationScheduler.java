package com.luxesuite.api.scheduler;

import com.luxesuite.api.model.Appointment;
import com.luxesuite.api.model.AppointmentStatus;
import com.luxesuite.api.model.Customer;
import com.luxesuite.api.repository.AppointmentRepository;
import com.luxesuite.api.repository.CustomerRepository;
import com.luxesuite.api.service.NotificationService;
import com.luxesuite.api.service.whatsapp.WhatsAppService;
import com.luxesuite.api.repository.WhatsAppMessageLogRepository;
import com.luxesuite.api.model.WhatsAppMessageLog;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final NotificationService notificationService;
    private final WhatsAppService whatsAppService;
    private final WhatsAppMessageLogRepository whatsappLogRepository;

    // Run every 15 mins for better precision
    @Scheduled(fixedRate = 900000) // 15 minutes
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "sendUpcomingAppointmentReminders_lock", lockAtMostFor = "14m", lockAtLeastFor = "1m")
    public void sendUpcomingAppointmentReminders() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Running Appointment Reminder Job at {}", now);
        
        // 24h Window: appointments starting between 23.5 and 24.5 hours from now
        LocalDateTime start24h = now.plusHours(23).plusMinutes(30);
        LocalDateTime end24h = now.plusHours(24).plusMinutes(30);
        List<Appointment> upcoming24h = appointmentRepository.findUpcomingFor24hReminder(start24h, end24h);
        for (Appointment apt : upcoming24h) {
            sendReminders(apt, "24 hours");
            apt.setReminder24hSent(true);
            appointmentRepository.save(apt);
        }

        // 2h Window: appointments starting between 1.5 and 2.5 hours from now
        LocalDateTime start2h = now.plusHours(1).plusMinutes(30);
        LocalDateTime end2h = now.plusHours(2).plusMinutes(30);
        List<Appointment> upcoming2h = appointmentRepository.findUpcomingFor2hReminder(start2h, end2h);
        for (Appointment apt : upcoming2h) {
            sendReminders(apt, "2 hours");
            apt.setReminder2hSent(true);
            appointmentRepository.save(apt);
        }
    }

    // --- Post-visit rebooking nudge (runs every hour) ---
    @Scheduled(fixedRate = 3600000)
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "processRebookingNudges_lock", lockAtMostFor = "59m", lockAtLeastFor = "1m")
    public void processRebookingNudges() {
        LocalDateTime now = LocalDateTime.now();
        // Target: appointments completed 2-4 hours ago
        LocalDateTime windowStart = now.minusHours(4);
        LocalDateTime windowEnd = now.minusHours(2);
        log.info("Running Rebooking Nudge Job at {}", now);

        List<Appointment> completed = appointmentRepository.findRecentlyCompletedForRebooking(windowStart, windowEnd);
        for (Appointment apt : completed) {
            String email = resolveEmail(apt);
            if (email != null) {
                String customerName = apt.getCustomer().getFirstName();
                notificationService.sendEmail(email,
                        "We loved seeing you today, " + customerName + "!",
                        "Hi " + customerName + ",\n\nThank you for your visit to Lumina Spa! "
                        + "To keep your results looking their best, we recommend booking your next session. "
                        + "Book now and enjoy priority scheduling.\n\n"
                        + "Book again: https://luminaspa.com/book\n\n"
                        + "See you soon!\n— The Lumina Spa Team");
            }
            apt.setRebookingNudgeSent(true);
            appointmentRepository.save(apt);
        }
        if (!completed.isEmpty()) {
            log.info("Sent {} rebooking nudge(s)", completed.size());
        }
    }

    // --- Win-back campaign for lapsed clients (runs daily at 9 AM) ---
    @Scheduled(cron = "0 0 9 * * *")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "processWinBackCampaigns_lock", lockAtMostFor = "23h", lockAtLeastFor = "1m")
    public void processWinBackCampaigns() {
        LocalDateTime now = LocalDateTime.now();
        // Target: customers whose last completed visit was 58-62 days ago (window)
        LocalDateTime lapsedStart = now.minusDays(62);
        LocalDateTime lapsedEnd = now.minusDays(58);
        log.info("Running Win-Back Campaign Job at {}", now);

        List<Appointment> lapsedAppointments = appointmentRepository.findLapsedCustomerAppointments(now, lapsedStart, lapsedEnd);
        
        // Deduplicate by customer so we only email each lapsed customer once
        Set<Long> notifiedCustomerIds = new HashSet<>();
        for (Appointment apt : lapsedAppointments) {
            if (apt.getCustomer() == null || notifiedCustomerIds.contains(apt.getCustomer().getId())) {
                continue;
            }
            String email = resolveEmail(apt);
            if (email != null) {
                String customerName = apt.getCustomer().getFirstName();
                notificationService.sendEmail(email,
                        "We miss you, " + customerName + "! Here's a treat",
                        "Hi " + customerName + ",\n\nIt's been a while since your last visit to Lumina Spa, "
                        + "and we'd love to welcome you back!\n\n"
                        + "Use code MISSYOU10 for 10% off your next booking.\n\n"
                        + "Book now: https://luminaspa.com/book\n\n"
                        + "We can't wait to see you again!\n— The Lumina Spa Team");
                notifiedCustomerIds.add(apt.getCustomer().getId());
            }
        }
        if (!notifiedCustomerIds.isEmpty()) {
            log.info("Sent {} win-back campaign email(s)", notifiedCustomerIds.size());
        }
    }

    // --- Birthday offers (runs daily at 8 AM) ---
    @Scheduled(cron = "0 0 9 * * *") // Every day at 9 AM
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "sendBirthdayGreetings_lock", lockAtMostFor = "23h", lockAtLeastFor = "1m")
    public void sendBirthdayGreetings() {
        LocalDate today = LocalDate.now();
        log.info("Running Birthday Offers Job for {}", today);

        List<Customer> birthdayCustomers = customerRepository.findByBirthdayMonthAndDay(
                today.getMonthValue(), today.getDayOfMonth());
        
        for (Customer customer : birthdayCustomers) {
            String email = customer.getUser() != null ? customer.getUser().getEmail() : customer.getEmail();
            if (email != null) {
                notificationService.sendEmail(email,
                        "Happy Birthday, " + customer.getFirstName() + "! A gift from Lumina Spa",
                        "Dear " + customer.getFirstName() + ",\n\n"
                        + "Wishing you the most wonderful birthday!\n\n"
                        + "As our gift to you, enjoy 15% off any service this month with code BDAY15.\n\n"
                        + "Treat yourself: https://luminaspa.com/book\n\n"
                        + "With love,\n— The Lumina Spa Team");
            }
            String phone = customer.getPhone();
            if (phone != null) {
                notificationService.sendSms(phone,
                        "Happy Birthday " + customer.getFirstName() + "! Enjoy 15% off at Lumina Spa with code BDAY15. Book now: luminaspa.com/book");
            }
        }
        if (!birthdayCustomers.isEmpty()) {
            log.info("Sent {} birthday offer(s)", birthdayCustomers.size());
        }
    }

    private void sendReminders(Appointment apt, String timeFrame) {
        String email = apt.getCustomer() != null && apt.getCustomer().getUser() != null ? apt.getCustomer().getUser().getEmail() : null;
        String phone = apt.getCustomer() != null ? apt.getCustomer().getPhone() : null;
        String startTime = apt.getServices() != null && !apt.getServices().isEmpty() ? apt.getServices().get(0).getStartTime().toString() : "TBD";
        
        if (email != null) {
            notificationService.sendEmail(email, "Appointment Reminder", "You have an appointment coming up in " + timeFrame + " at " + startTime);
        }
        if (phone != null) {
            notificationService.sendSms(phone, "Reminder: Your LuxeSuite appointment is in " + timeFrame + " at " + startTime);
            
            // WhatsApp integration
            if (Boolean.TRUE.equals(apt.getCustomer().getWhatsappOptIn())) {
                Map<String, String> params = new HashMap<>();
                params.put("time", startTime);
                params.put("customerName", apt.getCustomer().getFirstName());
                
                CompletableFuture.runAsync(() -> {
                    try {
                        String status = whatsAppService.sendMessage(phone, "appointment_reminder", params);
                        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                            .customer(apt.getCustomer())
                            .phoneNumber(phone)
                            .templateName("appointment_reminder")
                            .status(status)
                            .relatedEntityType("Appointment")
                            .relatedEntityId(apt.getId())
                            .build();
                        whatsappLogRepository.save(logEntry);
                    } catch (Exception e) {
                        log.error("Failed to send WhatsApp message to {}", phone, e);
                        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                            .customer(apt.getCustomer())
                            .phoneNumber(phone)
                            .templateName("appointment_reminder")
                            .status("FAILED")
                            .relatedEntityType("Appointment")
                            .relatedEntityId(apt.getId())
                            .build();
                        whatsappLogRepository.save(logEntry);
                    }
                });
            }
        }
    }

    private String resolveEmail(Appointment apt) {
        if (apt.getCustomer() == null) return null;
        if (apt.getCustomer().getUser() != null && apt.getCustomer().getUser().getEmail() != null) {
            return apt.getCustomer().getUser().getEmail();
        }
        return apt.getCustomer().getEmail();
    }
}
