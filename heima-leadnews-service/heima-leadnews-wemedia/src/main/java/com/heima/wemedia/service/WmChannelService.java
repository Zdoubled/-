package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmChannelDto;
import com.heima.model.wemedia.pojos.WmChannel;

public interface WmChannelService extends IService<WmChannel> {
    /**
     * 查询所有频道
     * @return
     */
    ResponseResult findAll();

    /**
     * 删除频道
     * @param id
     * @return
     */
    ResponseResult delete(Integer id);

    /**
     * 新增频道
     * @param wmChannel
     * @return
     */
    ResponseResult insert(WmChannel wmChannel);

    /**
     * 分页查询频道
     * @param dto
     * @return
     */
    ResponseResult listChannel(WmChannelDto dto);

    /**
     * 修改频道信息
     * @param wmChannel
     * @return
     */
    ResponseResult updateChannel(WmChannel wmChannel);
}
