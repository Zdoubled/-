package com.heima.behavior.controller;

import com.heima.behavior.service.ReadBehaviorService;
import com.heima.model.behavior.dtos.ArticleReadDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/read_behavior")
public class ReadBehaviorController {

    @Autowired
    private ReadBehaviorService readBehaviorService;

    @PostMapping
    private ResponseResult readBehavior(@RequestBody ArticleReadDto dto) {
        return readBehaviorService.readBehavior(dto);
    }

}
