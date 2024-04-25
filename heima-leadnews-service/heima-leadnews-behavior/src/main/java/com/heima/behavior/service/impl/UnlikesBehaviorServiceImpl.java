package com.heima.behavior.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.behavior.service.UnlikesBehaviorService;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behavior.dtos.UnlikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UnlikesBehaviorServiceImpl implements UnlikesBehaviorService {

    @Autowired
    private CacheService cacheService;
    /**
     * 不喜欢
     * @param dto
     * @return
     */
    @Override
    public ResponseResult unlikes(UnlikesBehaviorDto dto) {
        //1.参数校验
        if (dto == null || dto.getArticleId() == null || dto.getType() == null || dto.getType() < 0 || dto.getType() > 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //3.不喜欢
        Object o = cacheService.hGet(BehaviorConstants.UNLIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (dto.getType() == 1) {
            //取消不喜欢
            log.info("删除当前key:{}, {}", dto.getArticleId(), user.getId());
            if (o == null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"未不喜欢");
            }
            cacheService.hDelete(BehaviorConstants.UNLIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        }else {
            //不喜欢
            // 保存当前key
            log.info("保存当前key:{} ,{}, {}", dto.getArticleId(), user.getId(), dto);
            if (o != null) {
                return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(),"已不喜欢");
            }
            cacheService.hPut(BehaviorConstants.UNLIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString(), JSON.toJSONString(dto));
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
