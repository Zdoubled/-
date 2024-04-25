package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.article.service.ApCollectionService;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behavior.dtos.CollectionBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApCollectionServiceImpl implements ApCollectionService {

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult collect(CollectionBehaviorDto dto) {
        //1.参数校验
        if (dto == null || checkParams(dto)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.判断是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //3.查询是否收藏
        Object o = cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR+user.getId(), dto.getEntryId().toString());
        //4.收藏或取消收藏
        if (dto.getOperation() == 1) {
            //取消收藏
            if (o == null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID.getCode(), "未收藏收藏");
            }
            cacheService.hDelete(BehaviorConstants.COLLECTION_BEHAVIOR+user.getId().toString(), dto.getEntryId().toString());
        }else {
            //收藏
            if (o != null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID.getCode(), "已收藏");
            }
            cacheService.hPut(BehaviorConstants.COLLECTION_BEHAVIOR+user.getId().toString(), dto.getEntryId().toString(), JSON.toJSONString(dto));
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    public boolean checkParams(CollectionBehaviorDto dto) {
        if (dto.getEntryId() == null || dto.getOperation() == null || dto.getType() == null || dto.getPublishTime() == null
                || dto.getOperation() != 1 && dto.getOperation() != 0 || dto.getType() > 1 || dto.getType() < 0) {
            return false;
        }
        return true;
    }
}
