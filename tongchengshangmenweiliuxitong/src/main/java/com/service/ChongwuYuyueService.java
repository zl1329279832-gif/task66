package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.utils.R;
import com.entity.ChongwuYuyueEntity;
import java.util.Map;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import java.util.List;

/**
 * 宠物预约 服务类
 */
public interface ChongwuYuyueService extends IService<ChongwuYuyueEntity> {

    /**
    * @param params 查询参数
    * @return 带分页的查询出来的数据
    */
     PageUtils queryPage(Map<String, Object> params);

    /**
     * 检查同一宠物在同一预约时间是否存在冲突（已通过/进行中的预约）
     * @param chongwuId 宠物ID
     * @param yuyueTime 预约时间
     * @param excludeId 需要排除的预约ID（可为null）
     * @return true=存在冲突
     */
    boolean checkTimeConflict(Integer chongwuId, Date yuyueTime, Integer excludeId);

    /**
     * 审核预约：通过时占用名额（检查时间冲突），拒绝时释放名额
     * @param paramEntity 包含id、审核结果(yesnoTypes)、审核回复等
     * @return 操作结果
     */
    R shenhe(ChongwuYuyueEntity paramEntity);

    /**
     * 推进预约状态：已通过(2)->进行中(3)->已完成(4)
     * @param id 预约ID
     * @param yonghuId 当前操作用户ID（用于权限校验，管理员传null跳过校验）
     * @param isAdmin 是否管理员
     * @return 操作结果
     */
    R advanceStatus(Integer id, Integer yonghuId, boolean isAdmin);

    /**
     * 检查用户对该宠物是否存在已完成的预约（用于评价前置校验）
     * @param yonghuId 用户ID
     * @param chongwuId 宠物ID
     * @return true=存在已完成预约
     */
    boolean hasCompletedBooking(Integer yonghuId, Integer chongwuId);

}