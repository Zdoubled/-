package com.heima.behavior.service;

import com.heima.model.behavior.dtos.ArticleReadDto;
import com.heima.model.common.dtos.ResponseResult;

public interface ReadBehaviorService {
    /**
     * 
     * @param dto
     * @return
     */
    ResponseResult readBehavior(ArticleReadDto dto);
}
