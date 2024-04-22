package com.heima.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.user.pojos.ApUserRealname;
import com.heima.user.mapper.ApUserRealNameMapper;
import com.heima.user.service.ApUserRealNameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Slf4j
@Transactional
public class ApUserRealNameServiceImpl extends ServiceImpl<ApUserRealNameMapper, ApUserRealname> implements ApUserRealNameService {
    /**
     * 查询所有实名申请
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findAll(AuthDto dto) {
        //1.参数校验
        dto.checkParam();
        //2.分页查询
        LambdaQueryWrapper<ApUserRealname> wrapper = Wrappers.<ApUserRealname>lambdaQuery();
        //根据状态查询
        if (dto.getStatus() != null) {
            wrapper.eq(ApUserRealname::getStatus, dto.getStatus());
        }
        IPage<ApUserRealname> page = new Page<>(dto.getPage(),dto.getSize());
        page = page(page, wrapper);
        //3.结果返回
        PageResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        result.setData(page.getRecords());
        return result;
    }

    /**
     * 驳回
     * @param dto
     * @return
     */
    @Override
    public ResponseResult authFail(AuthDto dto) {
        //1.参数校验
        Integer id = dto.getId();
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询申请
        ApUserRealname apUserRealname = getById(id);
        if (null == apUserRealname) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //3.修改申请
        apUserRealname.setStatus((short)2);
        apUserRealname.setReason(dto.getMsg());
        apUserRealname.setUpdatedTime(new Date());
        updateById(apUserRealname);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult authPass(AuthDto dto) {
        //1.参数校验
        Integer id = dto.getId();
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询申请
        ApUserRealname apUserRealname = getById(id);
        if (null == apUserRealname) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //3.修改申请
        apUserRealname.setStatus((short)9);
        apUserRealname.setUpdatedTime(new Date());
        updateById(apUserRealname);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
