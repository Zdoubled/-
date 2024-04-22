package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.SensitiveDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.wemedia.service.WmSensitiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensitive")
public class WmSensitiveController {
    @Autowired
    private WmSensitiveService wmSensitiveService;

    /**
     * 查询敏感词
     * @param sensitiveDto
     * @return
     */
    @PostMapping("/list")
    public ResponseResult sensitiveList(@RequestBody SensitiveDto sensitiveDto) {
        return wmSensitiveService.sensitiveList(sensitiveDto);
    }

    /**
     * 删除敏感词
     * @param id
     * @return
     */
    @DeleteMapping("/del/{id}")
    public ResponseResult sensitiveDel(@PathVariable("id") Integer id) {
        return wmSensitiveService.sensitiveDel(id);
    }

    ///api/v1/sensitive/save

    /**
     * 新增敏感词
     * @return
     */
    @PostMapping("/save")
    public ResponseResult sensitiveSave(@RequestBody WmSensitive wmSensitive) {
        return wmSensitiveService.sensitiveSave(wmSensitive);
    }

    /**
     * 敏感词修改
     * @return
     */
    @PostMapping("/update")
    public ResponseResult sensitiveUpdate(@RequestBody WmSensitive wmSensitive) {
        return wmSensitiveService.sensitiveUpdate(wmSensitive);
    }
}
