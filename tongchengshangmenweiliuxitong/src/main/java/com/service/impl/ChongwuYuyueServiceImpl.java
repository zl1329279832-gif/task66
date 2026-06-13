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
import com.baomidou.mybatisplus.mapper.Wrapper;
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

/**
 * 宠物预约 服务实现类
 */
@Service("chongwuYuyueService")
@Transactional
public class ChongwuYuyueServiceImpl extends ServiceImpl<ChongwuYuyueDao, ChongwuYuyueEntity> implements ChongwuYuyueService {

    @Override
    public PageUtils queryPage(Map<String,Object> params) {
        Page<ChongwuYuyueView> page =new Query<ChongwuYuyueView>(params).getPage();
        page.setRecords(baseMapper.selectListView(page,params));
        return new PageUtils(page);
    }

    /**
     * 检查同一宠物在同一预约时间是否存在冲突
     * 只检查已通过(2)和进行中(3)的预约，排除自身
     */
    @Override
    public boolean checkTimeConflict(Integer chongwuId, Date yuyueTime, Integer excludeId) {
        if (chongwuId == null || yuyueTime == null) {
            return false;
        }
        Wrapper<ChongwuYuyueEntity> wrapper = new EntityWrapper<ChongwuYuyueEntity>()
                .eq("chongwu_id", chongwuId)
                .eq("chongwu_yuyue_time", yuyueTime)
                .in("chongwu_yuyue_types", new Integer[]{
                        ChongwuYuyueEntity.YUYUE_TYPE_APPROVED,
                        ChongwuYuyueEntity.YUYUE_TYPE_IN_PROGRESS
                });
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        List<ChongwuYuyueEntity> conflicts = this.selectList(wrapper);
        return conflicts != null && !conflicts.isEmpty();
    }

    /**
     * 审核预约
     * 通过(yesno=2)时：检查时间冲突 → 设置预约进度为"已通过/待进行"
     * 拒绝(yesno=3)时：直接标记拒绝，名额自动不占用（冲突检查只看已通过/进行中）
     */
    @Override
    public R shenhe(ChongwuYuyueEntity paramEntity) {
        ChongwuYuyueEntity existing = this.selectById(paramEntity.getId());
        if (existing == null) {
            return R.error(511, "预约记录不存在");
        }
        // 只有待审核(yesno=1) 且 进度为待审核(types=1) 的才能审核
        if (!Integer.valueOf(1).equals(existing.getChongwuYuyueYesnoTypes())) {
            return R.error(511, "该预约已审核，不可重复操作");
        }

        Integer yesnoResult = paramEntity.getChongwuYuyueYesnoTypes();
        if (yesnoResult == null) {
            return R.error(511, "审核结果不能为空");
        }

        if (Integer.valueOf(2).equals(yesnoResult)) {
            // 审核通过 → 检查时间冲突
            if (checkTimeConflict(existing.getChongwuId(), existing.getChongwuYuyueTime(), existing.getId())) {
                return R.error(511, "该宠物在此时间段已有预约，名额冲突，无法通过");
            }
            existing.setChongwuYuyueYesnoTypes(2);
            existing.setChongwuYuyueTypes(ChongwuYuyueEntity.YUYUE_TYPE_APPROVED);
        } else if (Integer.valueOf(3).equals(yesnoResult)) {
            // 审核拒绝 → 不占用名额
            existing.setChongwuYuyueYesnoTypes(3);
            // types 保持原值或设为一个"已拒绝"标记；这里不复用进度枚举，仅靠 yesno=3 标识
        } else {
            return R.error(511, "审核结果值非法，只能为2(通过)或3(拒绝)");
        }

        // 审核回复 & 审核时间
        if (paramEntity.getChongwuYuyueYesnoText() != null) {
            existing.setChongwuYuyueYesnoText(paramEntity.getChongwuYuyueYesnoText());
        }
        existing.setChongwuYuyueShenheTime(new Date());
        this.updateById(existing);
        return R.ok();
    }

    /**
     * 推进预约状态：已通过(2)→进行中(3)→已完成(4)
     */
    @Override
    public R advanceStatus(Integer id, Integer yonghuId, boolean isAdmin) {
        ChongwuYuyueEntity existing = this.selectById(id);
        if (existing == null) {
            return R.error(511, "预约记录不存在");
        }

        // 权限校验：普通用户只能操作自己的预约
        if (!isAdmin && !existing.getYonghuId().equals(yonghuId)) {
            return R.error(511, "无权操作此预约");
        }

        // 审核状态必须是通过
        if (!Integer.valueOf(2).equals(existing.getChongwuYuyueYesnoTypes())) {
            return R.error(511, "该预约未通过审核，无法推进状态");
        }

        Integer currentTypes = existing.getChongwuYuyueTypes();
        if (ChongwuYuyueEntity.YUYUE_TYPE_APPROVED.equals(currentTypes)) {
            // 2 → 3 进行中
            existing.setChongwuYuyueTypes(ChongwuYuyueEntity.YUYUE_TYPE_IN_PROGRESS);
        } else if (ChongwuYuyueEntity.YUYUE_TYPE_IN_PROGRESS.equals(currentTypes)) {
            // 3 → 4 已完成
            existing.setChongwuYuyueTypes(ChongwuYuyueEntity.YUYUE_TYPE_COMPLETED);
        } else if (ChongwuYuyueEntity.YUYUE_TYPE_COMPLETED.equals(currentTypes)) {
            return R.error(511, "该预约已完成，无需再次推进");
        } else {
            return R.error(511, "当前状态不允许推进");
        }

        this.updateById(existing);
        return R.ok();
    }

    /**
     * 检查用户对该宠物是否存在已完成的预约
     */
    @Override
    public boolean hasCompletedBooking(Integer yonghuId, Integer chongwuId) {
        Wrapper<ChongwuYuyueEntity> wrapper = new EntityWrapper<ChongwuYuyueEntity>()
                .eq("yonghu_id", yonghuId)
                .eq("chongwu_id", chongwuId)
                .eq("chongwu_yuyue_yesno_types", 2)
                .eq("chongwu_yuyue_types", ChongwuYuyueEntity.YUYUE_TYPE_COMPLETED);
        List<ChongwuYuyueEntity> list = this.selectList(wrapper);
        return list != null && !list.isEmpty();
    }

}
