package com.heima.behavior.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.heima.behavior.service.ReadBehaviorService;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behavior.dtos.ArticleReadDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReadBehaviorServiceImpl implements ReadBehaviorService {

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult readBehavior(ArticleReadDto dto) {
        //1.参数校验
        if (dto == null || dto.getArticleId() == null || dto.getCount() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //3.更新阅读次数
        String readBehaviorJson = (String) cacheService.hGet(BehaviorConstants.READ_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (StringUtils.isNotBlank(readBehaviorJson)) {
            //redis中有用户阅读信息，直接更新
            ArticleReadDto articleReadDto = JSON.parseObject(readBehaviorJson, ArticleReadDto.class);
            articleReadDto.setCount(articleReadDto.getCount() + dto.getCount());
        }
        //没有，则创建
        cacheService.hPut(BehaviorConstants.READ_BEHAVIOR + dto.getArticleId(), user.getId().toString(), JSON.toJSONString(dto));
        //4.TODO: 发送信息，数据聚合
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
