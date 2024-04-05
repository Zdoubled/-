package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

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
}
