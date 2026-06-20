package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    // User login
    private Limit login = new Limit();

    // Refresh token
    private Limit refreshToken = new Limit();

    // Admin login
    private Limit adminLogin = new Limit();

    // Verify admin OTP
    private Limit verifyAdminOtp = new Limit();

    // Resend admin OTP
    private Limit resendAdminOtp = new Limit();

    // Forgot password
    private Limit forgotPassword = new Limit();

    // Verify forgot password OTP
    private Limit verifyForgotPasswordOtp = new Limit();

    // Resend forgot password OTP
    private Limit resendForgotPasswordOtp = new Limit();

    // Reset password
    private Limit resetPassword = new Limit();

    // Register
    private Limit register = new Limit();

    // Logout
    private Limit logout = new Limit();

    // Chat API
    private Limit chat = new Limit();

    // Image generation API
    private Limit image = new Limit();

    // Vision API
    private Limit vision = new Limit();

    // PDF API
    private Limit pdf = new Limit();

    // Resume API
    private Limit resume = new Limit();

    // Website Generator API
    private Limit websiteGenerator = new Limit();

    // Create Order API
    private Limit createOrder = new Limit();

    // Verify Payment API
    private Limit verifyPayment = new Limit();

    // Payment History API
    private Limit paymentHistory = new Limit();

    // Create Subscription Plan API
    private Limit createPlan = new Limit();

    // Get Subscription Plans API
    private Limit getPlans = new Limit();

    // Update Subscription Plan API
    private Limit updatePlan = new Limit();

    // Delete Subscription Plan API
    private Limit deletePlan = new Limit();

    @Getter
    @Setter
    public static class Limit {

        // Maximum requests
        private long capacity;

        // Refill tokens
        private long refillTokens;

        // Refill duration in minutes
        private long refillMinutes;
    }
}