package com.heima.user.service.impl;

import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.UserRelationDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.service.ApUserRelationService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApUserRelationServiceImpl implements ApUserRelationService {

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult follow(UserRelationDto dto) {
        //1.参数校验
        if (null == dto || dto.getOperation() == null ||dto.getOperation() < 0 || dto.getOperation() > 1 || null == dto.getAuthorId()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.获取当前用户id
        ApUser user = AppThreadLocalUtil.getUser();
        if (null == user){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //3.判断操作 0关注,1取消关注
        if(dto.getOperation() == 0){
            //3.1关注
            //将关注对象存入当前用户关注列表中
            cacheService.zAdd(BehaviorConstants.APUSER_FOLLOW_RELATION+user.getId(),dto.getAuthorId().toString(), System.currentTimeMillis());
            //将当前用户存入关注对象的粉丝列表中
            cacheService.zAdd(BehaviorConstants.APUSER_FANS_RELATION+dto.getAuthorId(), user.getId().toString(), System.currentTimeMillis());
        }else {
            //3.2取消关注
            //将关注对象从当前用户关注列表中删除
            cacheService.zRemove(BehaviorConstants.APUSER_FOLLOW_RELATION+user.getId(),dto.getAuthorId().toString());
            //将当前用户从关注对象的粉丝列表中删除
            cacheService.zRemove(BehaviorConstants.APUSER_FANS_RELATION+dto.getAuthorId(), user.getId().toString());
        }
        //4.返回信息
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
