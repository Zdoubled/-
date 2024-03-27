package com.heima.article.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
}
