package com.heima.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.admin.mapper.AdUserMapper;
import com.heima.admin.service.AdUserService;
import com.heima.model.admin.dtos.AdUserDto;
import com.heima.model.admin.pojos.AdUser;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.utils.common.AdminJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Transactional
public class AdUserServiceImpl extends ServiceImpl<AdUserMapper, AdUser> implements AdUserService {
    /**
     * 管理员用户登录
     * @param dto
     * @return
     */
    @Override
    public ResponseResult loginIn(AdUserDto dto) {
        //1.条件判断
        if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getPassword())) {
            return ResponseResult.errorResult(1002,"数据不存在");
        }
        //2.查询数据库
        AdUser adUser = getOne(Wrappers.<AdUser>lambdaQuery().eq(AdUser::getName, dto.getName()));
        if (adUser == null) {
            //登录失败，返回错误信息
            return ResponseResult.errorResult(1002,"数据不存在");
        }
        //3.比对密码
        String salt = adUser.getSalt();
        String pswd = dto.getPassword();
        pswd = DigestUtils.md5DigestAsHex((pswd + salt).getBytes());
        if (!pswd.equals(adUser.getPassword())) {
            //登录失败，返回错误信息
            return ResponseResult.errorResult(2, "密码错误");
        }
        //4.返回数据  jwt
        Map<String,Object> map  = new HashMap<>();
        map.put("token", AdminJwtUtil.getToken(adUser.getId().longValue()));
        adUser.setSalt("");
        adUser.setPassword("");
        map.put("user",adUser);

        return ResponseResult.okResult(map);
    }
}
