package com.heima.model.wemedia.dtos;

import com.heima.model.common.dtos.PageRequestDto;
import lombok.Data;

@Data
public class WmChannelDto extends PageRequestDto {
    private String name;
    private Boolean status;
}
