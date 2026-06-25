package com.library.dto;

import jakarta.validation.constraints.NotNull;

public class PayRequest {

    @NotNull(message = "罚款ID不能为空")
    private Long id;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
