package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;
    /**
     * 查询文章
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {
        //1.参数校验
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //检查分页参数
        dto.checkParam();
        IPage page = new Page(dto.getPage(),dto.getSize());
        //2.分页条件查询
        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //状态精确查询
        lambdaQueryWrapper.eq(dto.getStatus()!=null,WmNews::getStatus,dto.getStatus());
        //时间范围精确查询
        if (dto.getBeginPubDate() != null && dto.getEndPubDate() != null) {
            lambdaQueryWrapper.between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }
        //频道精确查询
        lambdaQueryWrapper.eq(dto.getChannelId()!=null,WmNews::getChannelId,dto.getChannelId());
        //关键字频道查询
        lambdaQueryWrapper.like(dto.getKeyword()!=null,WmNews::getTitle,dto.getKeyword());
        //创建时间降序排序
        lambdaQueryWrapper.orderByDesc(WmNews::getPublishTime);

        page = page(page,lambdaQueryWrapper);
        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(dto.getPage(),dto.getSize(),(int)page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }

    /**
     * 发布修改文章或保存为草稿
     * @param dto
     * @return
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //1.条件判断
        if (dto == null || dto.getContent() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.保存或修改文章
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto,wmNews);
        //将图片列表转化为图片字符串，list -> String
        if (dto.getImages() != null && dto.getImages().size() > 0) {
            wmNews.setImages(StringUtils.join(dto.getImages(),","));
        }
        //如果当前封面类型为自动 -1，设为null
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        saveOrUpdateWmNews(wmNews);

        //3.判断是否存为草稿，是草稿直接结束方法
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        //4.不是存为草稿，保存文章内容与素材的联系
        //提取文章内容中的图片信息
        List<String> materials = extractUrlInfo(dto.getContent());
        saveRelationInfoForContent(materials,wmNews.getId());
        //5.保存文章封面图片与素材的联系，如果当前布局是自动，需要匹配封面图片
        saveRelationInfoForCover(dto, wmNews, materials);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 第一个功能：如果当前封面类型为自动，则设置封面类型的数据
     * 匹配规则：
     * 1，如果内容图片大于等于3  多图  type 3
     * 2，如果内容图片大于等于1，小于3  单图  type 1
     * 3，如果内容没有图片，无图  type 0
     *
     * 第二个功能：保存封面图片与素材的关系
     * @param dto
     * @param wmNews
     * @param materials
     */
    private void saveRelationInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {
        List<String> images = dto.getImages();

        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)) {
            if (materials.size() >= 3) {
                //多图
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            } else if (materials.size() >= 1) {
                //单图
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            } else {
                //无图
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);;
            }
            if (images != null && images.size() > 0) {
                wmNews.setImages(StringUtils.join(images,","));
            }
            updateById(wmNews);
        }
        if (images != null && images.size() > 0) {
            saveRelationInfo(materials, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
        }

    }

    /**
     * 处理文章内容与素材的关系
     * @param materials
     * @param WmNewsId
     */
    private void saveRelationInfoForContent(List<String> materials, Integer WmNewsId) {
        saveRelationInfo(materials,WmNewsId,WemediaConstants.WM_CONTENT_REFERENCE);
    }

    private void saveRelationInfo(List<String> materials, Integer wmNewsId, Short type) {
        //1.条件判断
        if (materials != null && !materials.isEmpty()){
            //2.根据图片url查询素材id
            List<WmMaterial> dbMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
            //判断素材是否有效
            if (dbMaterials == null || dbMaterials.size() == 0){
                //手动抛出异常   第一个功能：能够提示调用者素材失效了，第二个功能，进行数据的回滚
                throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
            }
            if(materials.size() != dbMaterials.size()){
                throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
            }

            List<Integer> idList = dbMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

            //3.保存关联关系
            wmNewsMaterialMapper.saveRelations(idList,wmNewsId,type);
        }
    }

    /**
     * 提取文章内容中的图片信息
     * @param content
     * @return
     */
    private List<String> extractUrlInfo(String content) {
        List<String> materials = new ArrayList<>();

        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if (map.get("type").equals("image")){
                materials.add((String) map.get("value"));
            }
        }
        return materials;
    }

    /**
     * 保存或修改文章
     * @param wmNews
     */
    private void saveOrUpdateWmNews(WmNews wmNews){
        //1.补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);//默认上架

        //2.判断新增还是修改
        if (wmNews.getId() == null){
            //新增
            save(wmNews);
        }else {
            //删除文章图片与素材的关系
            wmNewsMaterialMapper.delete(new LambdaQueryWrapper<WmNewsMaterial>().eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            //修改
            updateById(wmNews);
        }

    }
}
