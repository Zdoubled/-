package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;

public interface WmNewsService extends IService<WmNews> {
    /**
     * 查询文章
     * @param dto
     * @return
     */
    ResponseResult findAll(WmNewsPageReqDto dto);

    /**
     * 文章发布
     * @param dto
     * @return
     */
    ResponseResult submitNews(WmNewsDto dto);

    /**
     * 文章上下架
     * @param dto
     * @return
     */
    ResponseResult downOrUp(WmNewsDto dto);

    /**
     * 列出所有文章返回给admin
     * @param dto
     * @return
     */
    ResponseResult listAll(AuthDto dto);

    /**
     * 根据id查看文章详情
     * @param id
     * @return
     */
    ResponseResult checkDetail(Integer id);

    /**
     * 审核失败
     * @param authDto
     * @return
     */
    ResponseResult authFail(AuthDto authDto);

    /**
     * 审核通过
     * @param authDto
     * @return
     */
    ResponseResult authPass(AuthDto authDto);
}
