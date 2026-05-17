package com.example.mall.web.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        Long parentId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 128) String slug,
        Integer sort) {}
