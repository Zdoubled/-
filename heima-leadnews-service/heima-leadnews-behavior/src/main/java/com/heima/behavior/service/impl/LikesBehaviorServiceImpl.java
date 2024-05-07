package com.heima.behavior.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.behavior.service.LikesBehaviorService;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.constants.HotArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behavior.dtos.LikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.UpdateArticleMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LikesBehaviorServiceImpl implements LikesBehaviorService {

    @Autowired
    private CacheService cacheService;
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    @Override
    public ResponseResult likes(LikesBehaviorDto dto) {
        //1.参数校验
        if (dto == null || dto.getArticleId() == null  || dto.getOperation() == null || dto.getOperation() < 0 || dto.getOperation() > 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        UpdateArticleMess updateArticleMess = new UpdateArticleMess();
        updateArticleMess.setType(UpdateArticleMess.UpdateArticleType.LIKES);
        updateArticleMess.setArticleId(dto.getArticleId());

        //3.获取点赞对象信息
        Object o = cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (dto.getOperation() == 1) {
            //取消点赞
            log.info("删除当前key:{}, {}", dto.getArticleId(), user.getId());
            if (o == null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"未点赞");
            }
            cacheService.hDelete(BehaviorConstants.LIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
            updateArticleMess.setAdd(-1);
        }else {
            //点赞
            // 保存当前key
            log.info("保存当前key:{} ,{}, {}", dto.getArticleId(), user.getId(), dto);
            if (o != null) {
                return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(),"已点赞");
            }
            cacheService.hPut(BehaviorConstants.LIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString(), JSON.toJSONString(dto));
            updateArticleMess.setAdd(1);
        }
        //发送消息给kafka,数据聚合
        kafkaTemplate.send(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC, JSON.toJSONString(updateArticleMess));
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
