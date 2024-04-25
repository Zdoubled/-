package com.heima.behavior.service;

import com.heima.model.behavior.dtos.UnlikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;

public interface UnlikesBehaviorService {
    /**
     * 不喜欢
     * @param dto
     * @return
     */
    ResponseResult unlikes(UnlikesBehaviorDto dto);
}
