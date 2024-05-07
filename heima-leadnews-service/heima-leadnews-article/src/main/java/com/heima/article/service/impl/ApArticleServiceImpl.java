package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vos.HotArticleVO;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {
    //最大查询数量
    private static final Integer MAX_SIZE = 50;

    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;
    @Autowired
    private ApArticleContentMapper apArticleContentMapper;
    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;
    @Autowired
    private CacheService cacheService;

    //加载文章
    @Override
    public ResponseResult load(Short loadtype, ArticleHomeDto dto) {
        //1.参数校验
        //校验size
        Integer size = dto.getSize();
        if (size == null || size ==0){
            size = 10;
        }
        size = Math.min(size, MAX_SIZE);
        //类型参数检验
        if(!loadtype.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !loadtype.equals(ArticleConstants.LOADTYPE_LOAD_NEW)){
            loadtype = ArticleConstants.LOADTYPE_LOAD_MORE;
        }
        //校验tag
        String tag = dto.getTag();
        if (StringUtils.isBlank(tag)){
            tag = ArticleConstants.DEFAULT_TAG;
        }
        //校验maxBehotTime
        Date maxBehotTime = dto.getMaxBehotTime();
        if (maxBehotTime == null){
            maxBehotTime = new Date();
        }
        //校验minBehotTime
        Date minBehotTime = dto.getMinBehotTime();
        if (minBehotTime == null){
            minBehotTime = new Date();
        }
        //2.查询文章
        List<ApArticle> apArticles = apArticleMapper.loadArticleList(dto, loadtype);

        //3.结果返回
        return ResponseResult.okResult(apArticles);
    }

    @Override
    public ResponseResult load2(Short loadtype, ArticleHomeDto dto, Boolean firstPage) {
        if (firstPage){
            String JSONStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + dto.getTag());
            if (StringUtils.isNotBlank(JSONStr)){
                List<HotArticleVO> hotArticleVOS = JSON.parseArray(JSONStr, HotArticleVO.class);
                return ResponseResult.okResult(hotArticleVOS);
            }
        }
        return load(loadtype, dto);
    }

    /**
     * 保存app端相关文章
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {
/*        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        //1.参数校验
        if (dto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);
        //2.判断id是否为空
        if (dto.getId() == null){
            //为空，则保存文章 文章内容 文章配置
            //保存文章
            save(apArticle);
            //保存文章配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            //保存文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }else{
            //不为空，则修改文章 文章内容
            //修改文章
            updateById(apArticle);

            //修改文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, apArticle.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }
        //生成静态html，上传到minio
        articleFreemarkerService.buildArticleToMinIO(apArticle, dto.getContent());

        //3.返回文章id
        return ResponseResult.okResult(apArticle.getId());
    }

    /**
     * 回显用户操作文章行为信息
     * @param dto
     * @return
     */
    @Override
    public ResponseResult loadArticleBehavior(ArticleInfoDto dto) {
        //1.参数校验
        if (dto == null || dto.getArticleId() == null || dto.getAuthorId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.判断是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        boolean isLike = false, isUnLike = false, isCollection = false, isFollow = false;
        //3.查询用户是否点赞
        String likeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (StringUtils.isNotBlank(likeBehaviorJson)){
            isLike = true;
        }
        //4.查询用户是否收藏
        String collectionBehaviorJson = (String) cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (StringUtils.isNotBlank(collectionBehaviorJson)){
            isCollection = true;
        }
        //5.查询用户是否不喜欢
        String unlikeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.UNLIKE_BEHAVIOR + dto.getArticleId(), user.getId().toString());
        if (StringUtils.isNotBlank(unlikeBehaviorJson)){
            isUnLike = true;
        }
        //6.查询用户是否关注作者
        Double score = cacheService.zScore(BehaviorConstants.APUSER_FOLLOW_RELATION + dto.getAuthorId(), user.getId().toString());
        if (score != null){
            isFollow = true;
        }
        //7.结果集封装返回
        HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("islike", isLike);
        resultMap.put("isunlike", isUnLike);
        resultMap.put("iscollection", isCollection);
        resultMap.put("isfollow", isFollow);
        return ResponseResult.okResult(resultMap);
    }
    /**
     * 更新文章的分值  同时更新缓存中的热点文章数据
     * @param mess
     */
    @Override
    public void updateScore(ArticleVisitStreamMess mess) {
        //1.更新文章的阅读、点赞、收藏、评论的数量
        ApArticle apArticle = updateArticle(mess);
        //2.计算文章分值
        Integer score = computeScore(apArticle);
        score *= 3;
        //3.替换文章对应频道的热点数据
        replaceDataToRedis(apArticle, score, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId());
        //4.替换推荐的热点数据
        replaceDataToRedis(apArticle, score, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
    }
    /**
     * 替换数据并且存入到redis
     * @param apArticle
     * @param score
     * @param s
     */
    private void replaceDataToRedis(ApArticle apArticle, Integer score, String s) {
        String articleListStr = cacheService.get(s);
        if (StringUtils.isNotBlank(articleListStr)){
            List<HotArticleVO> hotArticleVOS = JSON.parseArray(articleListStr, HotArticleVO.class);
            boolean flag = true;
            //如果缓存中存在，只更新分值
            for (HotArticleVO hotArticleVO : hotArticleVOS) {
                if (hotArticleVO.getId().equals(apArticle.getId())){
                    hotArticleVO.setScore(score);
                    flag = false;
                    break;
                }
            }
            //如果缓存中不存在，将该文章添加进缓存的文章集合中，在进行排序
            if (flag){
                HotArticleVO hot = new HotArticleVO();
                BeanUtils.copyProperties(apArticle, hot);
                hot.setScore(score);
                hotArticleVOS.add(hot);
            }
            //排序并保留前30条数据
            hotArticleVOS = hotArticleVOS.stream().sorted(Comparator.comparing(HotArticleVO::getScore).reversed()).limit(30).collect(Collectors.toList());
            cacheService.set(s, JSON.toJSONString(hotArticleVOS));
        }
    }

    private ApArticle updateArticle(ArticleVisitStreamMess mess) {
        ApArticle apArticle = getById(mess.getArticleId());
        apArticle.setCollection(apArticle.getCollection()==null?0:apArticle.getCollection()+mess.getCollect());
        apArticle.setComment(apArticle.getComment()==null?0:apArticle.getComment()+mess.getComment());
        apArticle.setLikes(apArticle.getLikes()==null?0:apArticle.getLikes()+mess.getLike());
        apArticle.setViews(apArticle.getViews()==null?0:apArticle.getViews()+mess.getView());
        updateById(apArticle);
        return apArticle;
    }
    /**
     * 计算文章的具体分值
     * @param apArticle
     * @return
     */
    private Integer computeScore(ApArticle apArticle) {
        Integer score = 0;
        if(apArticle.getLikes() != null){
            score += apArticle.getLikes() * ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        if(apArticle.getViews() != null){
            score += apArticle.getViews();
        }
        if(apArticle.getComment() != null){
            score += apArticle.getComment() * ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        if(apArticle.getCollection() != null){
            score += apArticle.getCollection() * ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }

        return score;
    }
}
