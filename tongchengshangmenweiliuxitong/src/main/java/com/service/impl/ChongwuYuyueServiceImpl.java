package com.service.impl;

import com.utils.StringUtil;
import com.service.DictionaryService;
import com.utils.ClazzDiff;
import com.utils.R;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Field;
import java.util.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;
import com.utils.PageUtils;
import com.utils.Query;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import com.dao.ChongwuYuyueDao;
import com.entity.ChongwuYuyueEntity;
import com.service.ChongwuYuyueService;
import com.entity.view.ChongwuYuyueView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 宠物预约 服务实现类
 */
@Service("chongwuYuyueService")
@Transactional
public class ChongwuYuyueServiceImpl extends ServiceImpl<ChongwuYuyueDao, ChongwuYuyueEntity> implements ChongwuYuyueService {

    private static final Logger logger = LoggerFactory.getLogger(ChongwuYuyueServiceImpl.class);

    /** 预约时间冲突检测窗口（毫秒），同一宠物前后2小时内不允许重叠 */
    private static final long CONFLICT_WINDOW_MS = 2 * 60 * 60 * 1000L;

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        Page<ChongwuYuyueView> page =new Query<ChongwuYuyueView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }

    @Override
    public String checkTimeConflict(Integer chongwuId, Date yuyueTime, Integer excludeId) {
        if (chongwuId == null || yuyueTime == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(yuyueTime);
        cal.add(Calendar.MILLISECOND, (int) -CONFLICT_WINDOW_MS);
        Date windowStart = cal.getTime();
        cal.setTime(yuyueTime);
        cal.add(Calendar.MILLISECOND, (int) CONFLICT_WINDOW_MS);
        Date windowEnd = cal.getTime();

        EntityWrapper<ChongwuYuyueEntity> wrapper = new EntityWrapper<>();
        wrapper.eq("chongwu_id", chongwuId)
               .in("chongwu_yuyue_yesno_types", new Integer[]{1, 2, 4})
               .ge("chongwu_yuyue_time", windowStart)
               .le("chongwu_yuyue_time", windowEnd);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        List<ChongwuYuyueEntity> conflicts = this.selectList(wrapper);
        if (conflicts != null && !conflicts.isEmpty()) {
            return "该宠物在预约时间前后2小时内已有预约（单号: " + conflicts.get(0).getChongwuYuyueUuidNumber() + "），不能重复预约";
        }
        return null;
    }

    @Override
    public R shenhe(ChongwuYuyueEntity incoming) {
        if (incoming.getId() == null) {
            return R.error(511, "缺少预约ID");
        }
        ChongwuYuyueEntity old = this.selectById(incoming.getId());
        if (old == null) {
            return R.error(511, "预约记录不存在");
        }
        if (old.getChongwuYuyueYesnoTypes() != 1) {
            return R.error(511, "只有待审核状态的预约才能审核，当前状态: " + old.getChongwuYuyueYesnoTypes());
        }

        Integer decision = incoming.getChongwuYuyueYesnoTypes();
        if (decision == null || (decision != 2 && decision != 3)) {
            return R.error(511, "审核结果只能是 2(通过) 或 3(拒绝)");
        }

        if (decision == 2) {
            // 通过 → 占用名额前再次检查时间冲突（排除自身，只看已通过/进行中的）
            EntityWrapper<ChongwuYuyueEntity> wrapper = new EntityWrapper<>();
            Calendar cal = Calendar.getInstance();
            cal.setTime(old.getChongwuYuyueTime());
            cal.add(Calendar.MILLISECOND, (int) -CONFLICT_WINDOW_MS);
            Date windowStart = cal.getTime();
            cal.setTime(old.getChongwuYuyueTime());
            cal.add(Calendar.MILLISECOND, (int) CONFLICT_WINDOW_MS);
            Date windowEnd = cal.getTime();

            wrapper.eq("chongwu_id", old.getChongwuId())
                   .in("chongwu_yuyue_yesno_types", new Integer[]{2, 4})
                   .ge("chongwu_yuyue_time", windowStart)
                   .le("chongwu_yuyue_time", windowEnd)
                   .ne("id", old.getId());
            List<ChongwuYuyueEntity> conflicts = this.selectList(wrapper);
            if (conflicts != null && !conflicts.isEmpty()) {
                return R.error(511, "该宠物在此时间段已有审核通过的预约（单号: "
                        + conflicts.get(0).getChongwuYuyueUuidNumber() + "），不能重复通过");
            }
            old.setChongwuYuyueYesnoTypes(2);
            logger.info("预约审核通过，占用名额，id={}, chongwuId={}", old.getId(), old.getChongwuId());
        } else {
            // 拒绝 → 释放名额（状态置为3，该时间段允许其他人预约）
            old.setChongwuYuyueYesnoTypes(3);
            logger.info("预约审核拒绝，释放名额，id={}, chongwuId={}", old.getId(), old.getChongwuId());
        }

        old.setChongwuYuyueYesnoText(incoming.getChongwuYuyueYesnoText());
        old.setChongwuYuyueShenheTime(new Date());
        this.updateById(old);
        return R.ok();
    }

    @Override
    public R advanceStatus(Integer id, Integer operatorUserId, String role) {
        if (id == null) {
            return R.error(511, "缺少预约ID");
        }
        ChongwuYuyueEntity entity = this.selectById(id);
        if (entity == null) {
            return R.error(511, "预约记录不存在");
        }

        // 普通用户只能操作自己的预约
        if ("用户".equals(role) && !entity.getYonghuId().equals(operatorUserId)) {
            return R.error(511, "无权操作他人的预约");
        }

        int current = entity.getChongwuYuyueYesnoTypes();
        int next;
        if (current == 2) {
            next = 4; // 审核通过 → 进行中
        } else if (current == 4) {
            next = 5; // 进行中 → 已完成
        } else {
            return R.error(511, "当前状态不允许推进（当前状态: " + current + "，只有审核通过或进行中可推进）");
        }

        entity.setChongwuYuyueYesnoTypes(next);
        this.updateById(entity);
        logger.info("预约状态推进: id={}, {} -> {}", id, current, next);
        return R.ok();
    }

}
