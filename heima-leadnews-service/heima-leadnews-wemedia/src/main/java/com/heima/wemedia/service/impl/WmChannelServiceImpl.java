package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmChannelDto;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.service.WmChannelService;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Slf4j
@Transactional
public class WmChannelServiceImpl extends ServiceImpl<WmChannelMapper, WmChannel> implements WmChannelService {

    @Autowired
    private WmNewsService wmNewsService;
    /**
     * 查询所有频道
     * @return
     */
    @Override
    public ResponseResult findAll() {
        return ResponseResult.okResult(list());
    }

    @Override
    public ResponseResult delete(Integer id) {
        //1.参数校验
        if (null == id) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询数据
        WmChannel channel = getById(id);
        //3.判断是否存在
        if (null == channel) {
            //4.不存在，返回错误信息
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //5.存在，判断状态是否为禁用状态
        if (channel.getStatus()){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"启用状态不能删除");
        }
        //6.是否被文章关联
        int count = wmNewsService.count(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getChannelId, id).eq(WmNews::getStatus, WmNews.Status.PUBLISHED.getCode()));
        if (count > 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道已被文章关联");
        }
        //7.进行删除
        removeById(channel);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult insert(WmChannel wmChannel) {
        //1.参数校验
        if (null == wmChannel) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询是否存在同名频道
        WmChannel channel = getOne(Wrappers.<WmChannel>lambdaQuery().eq(WmChannel::getName, wmChannel.getName()));
        if (null != channel) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_EXIST,"频道已存在");
        }
        //3.补全信息
        wmChannel.setCreatedTime(new Date());
        wmChannel.setIsDefault(true);
        //4.新增
        save(wmChannel);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult listChannel(WmChannelDto dto) {
        //1.参数校验
        dto.checkParam();
        //2.分页查询
        LambdaQueryWrapper<WmChannel> wrapper = Wrappers.<WmChannel>lambdaQuery();
        if (null != dto.getName()) {
            wrapper.like(WmChannel::getName, dto.getName());
        }
        if (null != dto.getStatus()) {
            wrapper.eq(WmChannel::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(WmChannel::getCreatedTime);
        IPage<WmChannel> page = new Page<>(dto.getPage(),dto.getSize());
        page(page,wrapper);
        //3.结果返回
        PageResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        result.setData(page.getRecords());
        return result;
    }

    @Override
    public ResponseResult updateChannel(WmChannel wmChannel) {
        //1.参数校验
        if (null == wmChannel) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询数据库
        WmChannel channel = getOne(Wrappers.<WmChannel>lambdaQuery().eq(WmChannel::getId, wmChannel.getId()));
        if (null == channel){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"频道不存在");
        }
        //3.查看频道是否被关联
        int count = wmNewsService.count(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getChannelId, channel.getId()).eq(WmNews::getStatus, WmNews.Status.PUBLISHED.getCode()));
        //4.被文章引用则不能修改
        if (count > 0){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道被文章引用，不能修改");
        }
        //5.修改
        updateById(wmChannel);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
