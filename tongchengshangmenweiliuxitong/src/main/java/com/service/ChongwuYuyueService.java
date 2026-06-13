package com.service;

import com.baomidou.mybatisplus.service.IService;
import com.utils.PageUtils;
import com.utils.R;
import com.entity.ChongwuYuyueEntity;
import java.util.Date;
import java.util.Map;
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
     * 检查同一宠物在指定预约时间前后2小时内是否有冲突的预约（状态为待审核/审核通过/进行中）
     * @param chongwuId 宠物ID
     * @param yuyueTime 预约时间
     * @param excludeId 排除的预约ID（更新时排除自身），可为null
     * @return 冲突提示信息，null表示无冲突
     */
    String checkTimeConflict(Integer chongwuId, Date yuyueTime, Integer excludeId);

    /**
     * 审核预约：通过时检查时间冲突并占用名额，拒绝时释放名额
     * @param incoming 前端传入的审核数据（id, yesnoTypes, yesnoText）
     * @return R
     */
    R shenhe(ChongwuYuyueEntity incoming);

    /**
     * 推进预约状态：审核通过(2)→进行中(4)→已完成(5)
     * @param id 预约ID
     * @param operatorUserId 操作人用户ID
     * @param role 操作人角色
     * @return R
     */
    R advanceStatus(Integer id, Integer operatorUserId, String role);

}