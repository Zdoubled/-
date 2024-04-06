package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskinfoMapper taskinfoMapper;
    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;
    @Autowired
    private CacheService cacheService;

    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try {
            String key = type + "_" + priority;
            //从redis中拉取任务数据
            String taskStr = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if (StringUtils.isNotBlank(taskStr)) {
                task = JSON.parseObject(taskStr, Task.class);
                //消费任务，跟新数据
                updateDb(task.getTaskId(), ScheduleConstants.EXECUTED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("poll task error", e);
        }
        return task;
    }

    /**
     * 取消任务
     * @param taskId
     * @return
     */
    @Override
    public boolean cancelTask(long taskId) {
        boolean flag = false;
        //删除任务，更新任务信息
        Task task = updateDb(taskId, ScheduleConstants.CANCELLED);
        //删除redis的数据
        if (task != null) {
            removeTaskFromCache(task);
            flag = true;
        }

        return flag;
    }

    /**
     * 取消redis任务
     * @param task
     */
    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lRemove(ScheduleConstants.TOPIC + key, 0, JSON.toJSONString(task));
        }else {
            cacheService.zRemove(ScheduleConstants.FUTURE + key, JSON.toJSONString(task));
        }
    }

    /**
     * 删除任务，更新日志
     * @param taskId
     * @param status
     * @return
     */
    private Task updateDb(long taskId, int status) {
        Task task = null;
        try {
            //删除任务
            taskinfoMapper.deleteById(taskId);
            //更新任务日志
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        }catch (Exception e){
            log.error("删除任务失败", e);
        }
        return task;
    }

    /**
     * 保存任务
     * @param task
     * @return
     */
    @Override
    public long addTask(Task task) {
        //1.保存任务到DB中
        boolean success = saveTaskToDB(task);
        if (success) {
            //2.存入redis中
            saveTaskToCache(task);
        }
        //3.返回任务id
        return task.getTaskId();
    }

    /**
     * 把任务添加到redis中
     * @param task
     */
    private void saveTaskToCache(Task task) {
        //生成key
        String key = task.getTaskType() + "_" + task.getPriority();
        //获取预设时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTime = calendar.getTimeInMillis();

        //executeTime 小于 localTime,存入list
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lLeftPush(ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        }else if (task.getExecuteTime() <= nextScheduleTime) {
            //executeTime 大于 localTime 小于 futureTime,存入zset
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }


    }

    /**
     * 把任务保存到数据库中
     * @param task
     * @return
     */
    private boolean saveTaskToDB(Task task) {
        boolean flag = false;
        try {
            //保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            Date date = new Date(task.getExecuteTime());
            taskinfo.setExecuteTime(date);

            taskinfoMapper.insert(taskinfo);
            //设置taskID
            task.setTaskId(taskinfo.getTaskId());
            //保存任务日志数据
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogs.setVersion(1);

            taskinfoLogsMapper.insert(taskinfoLogs);
            flag = true;
        }catch (Exception e){
            e.printStackTrace();
        }
        return flag;
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh(){
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);

        if (StringUtils.isNotBlank(token)){
            log.info("未来数据定时刷新 --- 定时任务");

            //1.获取所有未来任务集合的key
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            for (String futureKey : futureKeys) {
                //2.获取当前数据的key
                String topicKey = futureKey.split(ScheduleConstants.FUTURE + "_")[1];
                //3.按照key和分值，查询符合条件的数据
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());

                if (!tasks.isEmpty()){
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    log.info("刷新任务成功,futureKey:{},topicKey:{}", futureKey, topicKey);
                }
            }
        }
    }
}
