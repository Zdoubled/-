package com.heima.behavior.service;

import com.heima.model.behavior.dtos.LikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;

public interface LikesBehaviorService {
    /**
     * 点赞或取消点赞
     * @param dto
     * @return
     */
    ResponseResult likes(LikesBehaviorDto dto);
}
