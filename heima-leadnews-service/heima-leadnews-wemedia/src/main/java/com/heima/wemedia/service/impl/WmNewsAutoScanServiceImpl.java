package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.apis.Article.IArticleClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.common.tess4j.Tess4jClient;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;

    /**
     * 自媒体文章审核
     * @param id 自媒体文章id
     */
    @Override
    @Async
    public void autoScanWmNews(Integer id) {
        //防止文章尚未保存完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //1.查询自媒体文章
        WmNews wmNews = wmNewsMapper.selectById(id);
        if (wmNews == null) {
            throw new RuntimeException("WmNewsAutoScanServiceImpl----自媒体文章不存在");
        }
        if (wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())) {
            //从内容中提取纯文本和图片
            Map<String, Object> map = handleTextAndImages(wmNews);

            //自管理的敏感词过滤
            boolean isSensitive = handleSensitiveScan((String) map.get("content"), wmNews);
            if(!isSensitive) return;

            //2.审核文本内容
            boolean isTextScan = handleTextScan((String) map.get("content"), wmNews);
            if (!isTextScan) {
                return;
            }
            //3.审核文章图片
            boolean isImageScan = handleImageScan((List<String>) map.get("images"), wmNews);
            if (!isImageScan) {
                return;
            }
        }
        //4.审核成功，保存app端相关文章数据
        ResponseResult responseResult = saveAppArticle(wmNews);
        if (!responseResult.getCode().equals(200)) {
            throw new RuntimeException("WmNewsAutoScanServiceImpl----保存app端相关文章数据失败");
        }
        //5.返回article_id
        wmNews.setArticleId((Long) responseResult.getData());
        //6.更新自媒体文章
        updateWmNews(wmNews, WmNews.Status.PUBLISHED.getCode(), "审核成功");
    }

    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;
    /**
     * 自管理的敏感词审核
     * @param content
     * @param wmNews
     * @return
     */
    private boolean handleSensitiveScan(String content, WmNews wmNews) {
        boolean flag = true;
        //1.获取所有敏感词
        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers.<WmSensitive>lambdaQuery().select(WmSensitive::getSensitives));
        List<String> sensitiveList = wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());
        //2.初始化敏感词库
        SensitiveWordUtil.initMap(sensitiveList);
        //3.查看内容是否包含敏感词
        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
        if (map.size() > 0) {
            //存在敏感词,更新文章状态
            updateWmNews(wmNews, WmNews.Status.FAIL.getCode(), "当前文章存在敏感内容" + map);
            flag = false;
        }
        return flag;
    }

    @Autowired
    private IArticleClient articleClient;
    @Autowired
    private WmChannelMapper wmChannelMapper;
    @Autowired
    private WmUserMapper wmUserMapper;

    public ResponseResult saveAppArticle(WmNews wmNews) {
        ArticleDto dto = new ArticleDto();
        //属性拷贝
        BeanUtils.copyProperties(wmNews, dto);
        //内容布局填充
        dto.setLayout(wmNews.getType());
        //频道
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if (wmChannel != null) {
            dto.setChannelName(wmChannel.getName());
        }
        //作者
        dto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if (wmUser != null) {
            dto.setAuthorName(wmUser.getName());
        }
        //设置文章id
        if (wmNews.getArticleId() != null){
            dto.setId(wmNews.getArticleId());
        }
        //设置创建时间
        dto.setCreatedTime(new Date());
        return articleClient.saveArticle(dto);
    }

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired
    private Tess4jClient tess4jClient;

    private boolean handleImageScan(List<String> images, WmNews wmNews) {
        boolean flag = true;

        if (images == null || images.size() == 0) {
            return flag;
        }
        //去重
        images = images.stream().distinct().collect(Collectors.toList());
        //类型转化
        List<byte[]> imageList = new ArrayList<>();
        try {
            for (String image : images) {
                byte[] bytes = fileStorageService.downLoadFile(image);

                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                BufferedImage imageFile = ImageIO.read(in);

                //进行图片识别
                String result = tess4jClient.doOCR(imageFile);
                //审核图片中识别的文字是否包含管理的敏感内容
                boolean isSensitive = handleSensitiveScan(result, wmNews);
                if (!isSensitive){
                    return isSensitive;
                }

                imageList.add(bytes);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Map map = greenImageScan.imageScanner(imageList);
            if (map != null) {
                //审核失败
                if (map.get("suggestion").equals("block")) {
                    flag = false;
                    updateWmNews(wmNews, WmNews.Status.FAIL.getCode(), "文章图片审核失败");
                }
                //人工审核
                if (map.get("suggestion").equals("review")) {
                    flag = false;
                    updateWmNews(wmNews, WmNews.Status.ADMIN_AUTH.getCode(), "文章存在不确定内容");
                }
            }
        } catch (Exception e) {
            flag = false;
            e.printStackTrace();
        }
        return flag;
    }

    @Autowired
    private GreenTextScan greenTextScan;

    /**
     * 审核纯文本内容
     * @param content
     * @param wmNews
     * @return
     */
    private boolean handleTextScan(String content, WmNews wmNews){
        boolean flag = true;

        if ((content + "" + wmNews.getTitle()).length() == 0) {
            return flag;
        }
        try {
            Map map = greenTextScan.greenTextScanner(content + "" + wmNews.getTitle());
            if (map != null) {
                //审核失败
                if (map.get("suggestion").equals("block")) {
                    flag = false;
                    updateWmNews(wmNews, WmNews.Status.FAIL.getCode(), "文章内容审核失败");
                }
                //人工审核
                if (map.get("suggestion").equals("review")) {
                    flag = false;
                    updateWmNews(wmNews, WmNews.Status.ADMIN_AUTH.getCode(), "文章存在不确定内容");
                }
            }
        } catch (Exception e) {
            flag = false;
            e.printStackTrace();
        }

        return flag;
    }

    private void updateWmNews(WmNews wmNews, Short status, String reason) {
        wmNews.setReason(reason);
        wmNews.setStatus(status);
        wmNewsMapper.updateById(wmNews);
    }

    private Map<String, Object> handleTextAndImages(WmNews wmNews) {
        //存储纯文本内容
        StringBuilder stringBuilder = new StringBuilder();
        //存储图片
        List<String> images = new ArrayList<>();
        //提取自媒体文章内容和图片
        if (StringUtils.isNotBlank(wmNews.getContent())) {
            List<Map> maps = JSON.parseArray(wmNews.getContent(), Map.class);
            for (Map map : maps) {
                if (map.get("type").equals("text")) {
                    stringBuilder.append(map.get("value"));
                } else if (map.get("type").equals("image")) {
                    images.add((String) map.get("value"));
                }
            }
        }
        //提取自媒体文章封面图片
        if (StringUtils.isNotBlank(wmNews.getImages())) {
            String[] split = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }

        Map<String, Object> resultMap = new HashMap<>();

        resultMap.put("content", stringBuilder.toString());
        resultMap.put("images", images);

        return resultMap;
    }
}
