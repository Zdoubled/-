package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.apis.schedule.IScheduleClient;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.utils.common.ProtostuffUtil;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

import static com.heima.model.common.enums.TaskTypeEnum.NEWS_SCAN_TIME;

@Service
@Slf4j
public class WmNewsTaskServiceImpl implements WmNewsTaskService {
    @Autowired
    private IScheduleClient scheduleClient;
    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;
    /**
     * 添加任务到延迟队列中
     * @param id  文章的id
     * @param publishTime  发布的时间  可以做为任务的执行时间
     */
    @Override
    @Async
    public void addNewsToTask(Integer id, Date publishTime) {
        log.info("添加任务到延迟服务中----begin");

        Task task = new Task();
        task.setTaskType(NEWS_SCAN_TIME.getTaskType());
        task.setPriority(NEWS_SCAN_TIME.getPriority());
        task.setExecuteTime(publishTime.getTime());
        WmNews wmNews = new WmNews();
        wmNews.setId(id);

        task.setParameters(ProtostuffUtil.serialize(wmNews));
        scheduleClient.addTask(task);
        log.info("添加任务到延迟服务中----end");
    }

    /**
     * 消费延迟队列数据
     */
    @Scheduled(fixedRate = 1000)
    @Override
    @SneakyThrows
    public void scanNewsByTask() {
        //调用服务
        ResponseResult responseResult = scheduleClient.poll(NEWS_SCAN_TIME.getTaskType(), NEWS_SCAN_TIME.getPriority());
        if(responseResult.getCode().equals(200) && responseResult.getData() != null){
            //获取队列中的数据进行消费
            String taskStr = JSON.toJSONString(responseResult.getData());
            Task task = JSON.parseObject(taskStr, Task.class);//获取任务对象

            WmNews wmNews = ProtostuffUtil.deserialize(task.getParameters(), WmNews.class);//文章对象反序列
            wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        }
    }


}
