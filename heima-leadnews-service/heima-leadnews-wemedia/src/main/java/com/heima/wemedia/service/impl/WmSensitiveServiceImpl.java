package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.SensitiveDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.service.WmSensitiveService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class WmSensitiveServiceImpl extends ServiceImpl<WmSensitiveMapper, WmSensitive> implements WmSensitiveService {

    /**
     * 查询敏感词库
     * @param dto
     * @return
     */
    @Override
    public ResponseResult sensitiveList(SensitiveDto dto) {
        //1.参数校验
        dto.checkParam();
        //2.分页查询
        IPage<WmSensitive> iPage = new Page<>(dto.getPage(),dto.getSize());
        LambdaQueryWrapper<WmSensitive> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(dto.getName())) {
            wrapper.like(WmSensitive::getSensitives,dto.getName());
        }
        wrapper.orderByDesc(WmSensitive::getCreatedTime);
        iPage = this.page(iPage, wrapper);

        //3.结果封装返回
        PageResponseResult result = new PageResponseResult(dto.getPage(),dto.getSize(),(int)iPage.getTotal());
        result.setData(iPage.getRecords());
        return result;
    }

    /**
     * 删除敏感词
     * @param id
     * @return
     */
    @Override
    public ResponseResult sensitiveDel(Integer id) {
        //1.参数校验
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询数据库是否存在
        WmSensitive wmSensitive = getById(id);
        if (null == wmSensitive) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"该敏感词不存在");
        }
        //3.删除
        removeById(id);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 新增
     * @param wmSensitive
     * @return
     */
    @Override
    public ResponseResult sensitiveSave(WmSensitive wmSensitive) {
        //1.参数校验
        if (null == wmSensitive){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询数据库是否已经存在
        WmSensitive sensitive = getOne(Wrappers.<WmSensitive>lambdaQuery().eq(WmSensitive::getSensitives, wmSensitive.getSensitives()));
        if (null != sensitive) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_EXIST,"该敏感词已存在");
        }
        //3.新增
        wmSensitive.setCreatedTime(new Date());
        save(wmSensitive);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 修改
     * @param wmSensitive
     * @return
     */
    @Override
    public ResponseResult sensitiveUpdate(WmSensitive wmSensitive) {
        //1.参数校验
        if (null == wmSensitive){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询数据
        WmSensitive sensitive = getById(wmSensitive.getId());
        if (null == sensitive) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"该敏感词不存在");
        }

        //3.存在则修改
        //判断修改后的敏感词是否存在
        WmSensitive one = getOne(Wrappers.<WmSensitive>lambdaQuery().eq(WmSensitive::getSensitives, wmSensitive.getSensitives()));
        if (null != one) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_EXIST,"该敏感词已存在");
        }
        //2.不存在，修改
        sensitive.setSensitives(wmSensitive.getSensitives());
        updateById(sensitive);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
