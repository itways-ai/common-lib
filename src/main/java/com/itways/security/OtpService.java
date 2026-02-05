package com.itways.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final long OTP_TTL = 300000; // 5 minutes

    @Value("${otp.bypass:false}")
    private boolean otpByPass;

    @Value("${otp.bypass.value:111111}")
    private String otpByPassVal;

    public String generateOtp(String username) {
        String otp;

        // Check if OTP bypass is enabled
        if (otpByPass) {
            otp = otpByPassVal;
            System.out.println("OTP Bypass enabled - Using fixed OTP: " + otp);
        } else {
            otp = String.format("%06d", random.nextInt(1000000));
        }

        otpStore.put(username, new OtpData(otp, System.currentTimeMillis() + OTP_TTL));
        return otp;
    }

    public boolean validateOtp(String username, String otp) {
        OtpData data = otpStore.get(username);
        if (data == null)
            return false;

        if (System.currentTimeMillis() > data.expiryTime) {
            otpStore.remove(username);
            return false;
        }

        boolean isValid = data.otp.equals(otp);
        if (isValid) {
            otpStore.remove(username); // One-time use
        }
        return isValid;
    }

    private static class OtpData {
        private final String otp;
        private final long expiryTime;

        public OtpData(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }
}
