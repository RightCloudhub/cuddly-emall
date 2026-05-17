package com.example.mall.web.user;

import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.NotFoundException;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser AuthenticatedUser principal) {
        User user =
                userRepository
                        .findById(principal.id())
                        .orElseThrow(() -> new NotFoundException("user not found"));
        return new UserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
