package com.heima.model.article.vos;

import com.heima.model.article.pojos.ApArticle;
import lombok.Data;

@Data
public class HotArticleVO extends ApArticle {
    private Integer score;
}
