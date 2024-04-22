package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.model.wemedia.vo.WmNewsVo;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import com.heima.wemedia.service.WmUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;
    @Autowired
    private WmMaterialMapper wmMaterialMapper;
    @Autowired
    private WmNewsTaskService wmNewsTaskService;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private WmUserService wmUserService;
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

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;
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

        //6.审核文章
        //wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        wmNewsTaskService.addNewsToTask(wmNews.getId(), wmNews.getPublishTime());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 文章上下架
     * @param dto
     * @return
     */
    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        //1.判断文章是否存在
        Integer id = dto.getId();
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = getById(id);
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章不存在");
        }
        //2.判断文章状态是否为已上架
        if (!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "文章已下架");
        }
        //3.判断dto参数是否合法
        if (dto.getEnable() != null && dto.getEnable() > -1 && dto.getEnable() < 2) {
            //4.修改文章上下架状态
            update(Wrappers.<WmNews>lambdaUpdate().set(WmNews::getEnable, dto.getEnable()).eq(WmNews::getId, id));
        }
        //5.发送消息，通知article修改文章配置
        if (wmNews.getArticleId() != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("articleId", wmNews.getArticleId());
            map.put("enable", dto.getEnable());
            kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
        }
        //6.返回
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 列出所有文章返回给admin
     * @param dto
     * @return
     */
    @Override
    public ResponseResult listAll(AuthDto dto) {
        //1.参数校验
        dto.checkParam();
        //2.分页查询
        LambdaQueryWrapper<WmNews> wrapper = Wrappers.<WmNews>lambdaQuery();
        //根据标题查询
        if (null != dto.getTitle()){
            wrapper.like(WmNews::getTitle, dto.getTitle());
        }
        //根据状态查询
        if (null != dto.getStatus()){
            wrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        //创建时间降序排序
        wrapper.orderByDesc(WmNews::getCreatedTime);
        IPage<WmNews> page = new Page<WmNews>(dto.getPage(), dto.getSize());
        page = page(page, wrapper);
        //封装vo对象
        List<WmNews> wmNews = page.getRecords();
        List<WmNewsVo> wmNewsVos = wmNews.stream().map(wmNew -> {
            WmNewsVo wmNewsVo = new WmNewsVo();
            BeanUtils.copyProperties(wmNew, wmNewsVo);
            WmUser wmUser = wmUserService.getOne(Wrappers.<WmUser>lambdaQuery().eq(WmUser::getId, wmNew.getUserId()));
            wmNewsVo.setAuthorName(wmUser.getName());
            return wmNewsVo;
        }).collect(Collectors.toList());

        //3.封装返回
        PageResponseResult result = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        result.setData(wmNewsVos);
        return result;
    }

    @Override
    public ResponseResult checkDetail(Integer id) {
        //1.参数校验
        if (null == id){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询数据库
        WmNews wmNew = getById(id);
        if (wmNew == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmUser wmUser = wmUserService.getById(wmNew.getUserId());
        WmNewsVo wmNewsVo = new WmNewsVo();
        BeanUtils.copyProperties(wmNew, wmNewsVo);
        wmNewsVo.setAuthorName(wmUser.getName());
        //3.封装返回
        return ResponseResult.okResult(wmNewsVo);
    }

    /**
     * 审核失败
     * @param authDto
     * @return
     */
    @Override
    public ResponseResult authFail(AuthDto authDto) {
        //1.参数校验
        Integer id = authDto.getId();
        if (null == id){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询文章
        WmNews wmNews = getById(id);
        if (wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //3.判断文章状态
        if (!wmNews.getStatus().equals(WmNews.Status.ADMIN_AUTH.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //4.修改文章状态，并拒绝
        if (authDto.getMsg() != null){
            wmNews.setReason(authDto.getMsg());
        }
        wmNews.setStatus(WmNews.Status.FAIL.getCode());
        updateById(wmNews);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 审核通过
     * @param authDto
     * @return
     */
    @Override
    public ResponseResult authPass(AuthDto authDto) {
        //1.参数校验
        Integer id = authDto.getId();
        if (null == id){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.根据id查询文章
        WmNews wmNews = getById(id);
        if (wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //3.创建app端文章
        ResponseResult result = wmNewsAutoScanService.saveAppArticle(wmNews);
        //4.修改文章状态
        if(result.getCode().equals(200)){
            wmNews.setArticleId((Long) result.getData());
            wmNews.setStatus(WmNews.Status.PUBLISHED.getCode());
            updateById(wmNews);
        }

        return result.okResult(AppHttpCodeEnum.SUCCESS);
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
