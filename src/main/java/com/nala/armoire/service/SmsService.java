package com.nala.armoire.service;


import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * SMS Service - Sends OTP via Twilio
 * Alternative implementations: AWS SNS, MSG91, etc.
 */
@Slf4j
@Service
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String fromPhoneNumber;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio SMS service initialized");
    }

    /**
     * Send OTP via SMS
     */
    public void sendOtp(String toPhone, String otp) {
        try {
            String messageBody = String.format(
                "Your Armoire verification code is: %s\n\nValid for 5 minutes.\n\nDo not share this code with anyone.",
                otp
            );

            Message message = Message.creator(
                new PhoneNumber(toPhone),
                new PhoneNumber(fromPhoneNumber),
                messageBody
            ).create();

            log.info("SMS sent successfully. SID: {}, To: {}", 
                     message.getSid(), maskPhone(toPhone));

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", maskPhone(toPhone), e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again later.");
        }
    }

    /**
     * Send welcome SMS after registration
     */
    public void sendWelcomeSms(String toPhone, String userName) {
        try {
            String messageBody = String.format(
                "Welcome to Armoire, %s! 🎉\n\nYour account has been created successfully. Happy shopping!",
                userName != null ? userName : "User"
            );

            Message.creator(
                new PhoneNumber(toPhone),
                new PhoneNumber(fromPhoneNumber),
                messageBody
            ).create();

            log.info("Welcome SMS sent to: {}", maskPhone(toPhone));

        } catch (Exception e) {
            log.error("Failed to send welcome SMS: {}", e.getMessage());
            // Don't throw exception for non-critical SMS
        }
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }
}