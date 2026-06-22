package com.apigateway.dto;

import com.apigateway.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank
    @Size(max = 50)
    private String username;

    @Size(min = 6, max = 100)
    private String password;

    @Size(max = 100)
    private String displayName;

    @NotNull
    private UserRole role;

    private Boolean enabled = true;
}
