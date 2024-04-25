package com.heima.model.behavior.dtos;

import lombok.Data;

import java.util.Date;

@Data
public class CollectionBehaviorDto {
    private Long EntryId;
    private Short operation;
    private Date publishTime;
    private Short type;
}
