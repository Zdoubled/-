package com.heima.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.user.pojos.ApUserRealname;

public interface ApUserRealNameService extends IService<ApUserRealname> {
    /**
     * 查询所有实名申请
     * @param dto
     * @return
     */
    ResponseResult findAll(AuthDto dto);

    /**
     * 驳回
     * @param dto
     * @return
     */
    ResponseResult authFail(AuthDto dto);

    /**
     * 通过
     * @param dto
     * @return
     */
    ResponseResult authPass(AuthDto dto);
}
