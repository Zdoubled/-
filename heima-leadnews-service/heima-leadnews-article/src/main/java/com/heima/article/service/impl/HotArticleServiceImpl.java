package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.apis.wemedia.IWemediaClient;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.HotArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vos.HotArticleVO;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.heima.common.constants.ArticleConstants.*;

@Service
public class HotArticleServiceImpl implements HotArticleService {
    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private IWemediaClient wemediaClient;
    @Autowired
    private CacheService cacheService;

    @Override
    public void computeHotArticle() {
        //1.查询前五天的文章数据
        Date date = DateTime.now().minusDays(5).toDate();
        List<ApArticle> apArticleList = apArticleMapper.findArticleListByLast5days(date);
        //2.计算文章分数
        List<HotArticleVO> hotArticleVOS = computeHotArticle(apArticleList);
        //3.为每个频道缓存30条文章到redis中
        cacheTagToRedis(hotArticleVOS);
        //4.
    }

    private void cacheTagToRedis(List<HotArticleVO> hotArticleVOS) {
        //1.调用feign接口,查询所有频道
        ResponseResult result = wemediaClient.getChannels();
        if (result.getCode().equals(200)){
            String channelJSON = JSON.toJSONString(result.getData());
            List<WmChannel> wmChannels = JSON.parseArray(channelJSON, WmChannel.class);
            if (wmChannels != null && wmChannels.size() > 0){
                for (WmChannel wmChannel : wmChannels) {
                    List<HotArticleVO> hotArticleVOList = hotArticleVOS.stream().filter(hotArticleVO -> {
                        return hotArticleVO.getChannelId().equals(wmChannel.getId());
                    }).collect(Collectors.toList());
                    sortAndCache(hotArticleVOList, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + wmChannel.getId());
                }
            }
            //设置推荐数据
            sortAndCache(hotArticleVOS, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + DEFAULT_TAG);
        }
    }

    /**
     * 排序并缓存数据
     * @param hotArticleVOList
     * @param key
     */
    private void sortAndCache(List<HotArticleVO> hotArticleVOList, String key) {
        List<HotArticleVO> hotArticleVOS = hotArticleVOList.stream().sorted(Comparator.comparing(HotArticleVO::getScore).reversed()).collect(Collectors.toList());
        if (hotArticleVOS.size() > 30){
            hotArticleVOS = hotArticleVOS.subList(0, 30);
        }
        cacheService.set(key, JSON.toJSONString(hotArticleVOS));
    }

    /**
     * 计算文章热度
     * @param apArticleList
     * @return
     */
    private List<HotArticleVO> computeHotArticle(List<ApArticle> apArticleList) {
        List<HotArticleVO> hotArticleVOList = null;
        if (apArticleList != null){
            hotArticleVOList = apArticleList.stream().map(apArticle -> {
                HotArticleVO hotArticleVO = new HotArticleVO();
                BeanUtils.copyProperties(apArticle, hotArticleVO);
                Integer score = computeScore(apArticle);
                hotArticleVO.setScore(score);
                return hotArticleVO;
            }).collect(Collectors.toList());
        }
        return hotArticleVOList;
    }

    /**
     * 计算具体得分
     * @param apArticle
     * @return
     */
    private Integer computeScore(ApArticle apArticle) {
        Integer score = 0;
        if (apArticle.getLikes() != null){
            score += apArticle.getLikes() * HOT_ARTICLE_LIKE_WEIGHT;
        }
        if (apArticle.getViews() != null){
            score += apArticle.getViews();
        }
        if (apArticle.getCollection() != null){
            score += apArticle.getCollection() * HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        if (apArticle.getComment() != null){
            score += apArticle.getComment() * HOT_ARTICLE_COMMENT_WEIGHT;
        }
        return score;
    }
}
