package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.model.dto.request.*;
import com.nala.armoire.exception.UnauthorizedException;
import com.nala.armoire.model.dto.response.AuthResponse;
import com.nala.armoire.model.dto.response.MessageResponse;
import com.nala.armoire.model.dto.response.UserResponse;
import com.nala.armoire.model.entity.RefreshToken;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.model.entity.UserRole;
import com.nala.armoire.repository.RefreshTokenRepository;
import com.nala.armoire.repository.UserRepository;
import com.nala.armoire.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    @Value("${app.verification-token-expiration}")
    private Long verificationTokenExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        //check if email already exists
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        //create user
        User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .phone(request.getPhone())
                    .userName(request.getUserName())
                    .role(UserRole.CUSTOMER)
                    .isActive(true)
                    .emailVerified(false)
                    .verificationToken(UUID.randomUUID().toString())
                    .verificationTokenExpiresAt(
                        LocalDateTime.now().plusSeconds(verificationTokenExpiration / 1000)
                    ).build();

        user = userRepository.save(user);

        emailService.sendVerificationEmail(user);

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        System.out.println("LOGIN SERVICE - Starting");
        System.out.println("Email: " + request.getEmail());

        // Authentication user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        System.out.println("Authentication successful");

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Get the user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid User Credentials"));

        System.out.println("User found: " + user.getEmail());
//        System.out.println("User active: " + user.getIsActive());

        if(!user.getIsActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        // generate tokens
        return generateAuthResponse(user);
    }
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String requestToken = request.getRefreshToken();

        //validate the refresh token
        if(!tokenProvider.validateToken(requestToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        //find refresh token in the database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        //check if revoked or expired
        if(refreshToken.getRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token is revoked or expired");
        }

        //get user
        User user = refreshToken.getUser();

        //generate new access token

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(requestToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(mapToUserResponse(user))
                .build();
    }


    @Transactional
    public MessageResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if(user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token is expired");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        return MessageResponse.builder()
                .message("Email verified successfully")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiresAt(
                LocalDateTime.now().plusHours(1)
        );
        userRepository.save(user);

        // Send reset email
        emailService.sendPasswordResetEmail(user, resetToken);

        return MessageResponse.builder()
                .message("Password reset email sent")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);

        return MessageResponse.builder()
                .message("Password reset successful")
                .success(true)
                .build();
    }

    @Transactional
    public MessageResponse logout(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Revoke all refresh tokens
        refreshTokenRepository.deleteByUser(user);

        return MessageResponse.builder()
                .message("Logged out successfully")
                .success(true)
                .build();
    }

    private AuthResponse generateAuthResponse(User user) {
        // Generate access token
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // Generate refresh token
        String refreshTokenString = tokenProvider.generateRefreshToken(user.getId());

        // Save refresh token to database
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenString)
                .user(user)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
