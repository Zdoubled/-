package com.heima.model.behavior.dtos;

import lombok.Data;

@Data
public class ArticleReadDto {

    /**
     * 文章id
     */
    private Long articleId;
    /**
     * 阅读次数
     */
    private Integer count;
}
