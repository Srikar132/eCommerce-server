package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.Response.ApiResponse;
import com.nala.armoire.model.dto.Response.UserResponse;
import com.nala.armoire.model.entity.User;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    ResponseEntity<ApiResponse<UserResponse>> getUserProfile(@CurrentUser UserPrincipal currentUser) {

        UserResponse profile = userService.getUserProfile(currentUser.getId());

        return ResponseEntity.ok(
                ApiResponse.success(profile)
        );

    }


}
