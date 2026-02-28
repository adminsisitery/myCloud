package com.ff.mycloud.service;

import com.ff.mycloud.model.dto.LoginDTO;
import com.ff.mycloud.model.dto.RegisterDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public interface UserService {
    String login(LoginDTO loginDTO);
    void register(RegisterDTO registerDTO);
    void sendVerificationCode(@NotBlank @Email String email);
}
