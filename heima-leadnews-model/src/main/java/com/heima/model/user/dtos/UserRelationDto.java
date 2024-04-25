package com.heima.model.user.dtos;

import lombok.Data;

@Data
public class UserRelationDto {
    private Long articleId;
    private Integer authorId;
    private Short operation;
}
