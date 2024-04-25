package com.heima.behavior.controller;

import com.heima.behavior.service.LikesBehaviorService;
import com.heima.model.behavior.dtos.LikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/likes_behavior")
public class LikesBehaviorController {

    @Autowired
    private LikesBehaviorService likesBehaviorService;

    @PostMapping
    public ResponseResult likesBehavior(@RequestBody LikesBehaviorDto dto) {
        return likesBehaviorService.likes(dto);
    }
}
