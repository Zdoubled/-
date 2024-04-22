package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.SensitiveDto;
import com.heima.model.wemedia.pojos.WmSensitive;

public interface WmSensitiveService extends IService<WmSensitive> {

    /**
     * 查询敏感词库
     * @param dto
     * @return
     */
    public ResponseResult sensitiveList(SensitiveDto dto);

    /**
     * 删除敏感词
     * @param id
     * @return
     */
    ResponseResult sensitiveDel(Integer id);

    /**
     * 新增敏感词
     * @param wmSensitive
     * @return
     */
    ResponseResult sensitiveSave(WmSensitive wmSensitive);

    /**
     * 修改敏感词
     * @param wmSensitive
     * @return
     */
    ResponseResult sensitiveUpdate(WmSensitive wmSensitive);
}
