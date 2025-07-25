package com.alibaba.csp.sentinel.dashboard.controller.v2; // 建议为V2创建一个新的包

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.ParamFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controller for Parameter Flow Rule (V2), for Nacos persistence.
 *
 * @author Your Name
 */
@RestController
@RequestMapping(value = "/v2/paramFlow") // 使用 /v2 路径以区分
public class ParamFlowRuleControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(ParamFlowRuleControllerV2.class);

    @Autowired
    private RuleRepository<ParamFlowRuleEntity, Long> repository;

    @Autowired
    @Qualifier("paramFlowRuleNacosProvider")
    private DynamicRuleProvider<List<ParamFlowRuleEntity>> ruleProvider;

    @Autowired
    @Qualifier("paramFlowRuleNacosPublisher")
    private DynamicRulePublisher<List<ParamFlowRuleEntity>> rulePublisher;

    @Autowired
    private AuthService authService;

    private final ReentrantLock lock = new ReentrantLock();

    @GetMapping("/rules")
    @AuthAction(AuthService.PrivilegeType.READ_RULE)
    public Result<List<ParamFlowRuleEntity>> apiQueryRules(String app) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<ParamFlowRuleEntity> rules = ruleProvider.getRules(app);
            // 同步到内存仓库，以便于删除时能通过id找到app
            repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying parameter flow rules from Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        }
    }

    @PostMapping("/rule")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<ParamFlowRuleEntity> apiAddRule(@RequestBody ParamFlowRuleEntity entity) {
        Result<ParamFlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        entity.setId(null);
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);

        lock.lock();
        try {
            entity = repository.save(entity);
            List<ParamFlowRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            rules.add(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when adding new parameter flow rule to Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<ParamFlowRuleEntity> apiUpdateRule(@PathVariable("id") Long id,
                                                     @RequestBody ParamFlowRuleEntity entity) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        Result<ParamFlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        entity.setId(id);
        Date date = new Date();
        entity.setGmtModified(date);

        lock.lock();
        try {
            List<ParamFlowRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            rules.removeIf(r -> r.getId().equals(id));
            rules.add(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when updating parameter flow rule in Nacos, id=" + id, throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.DELETE_RULE)
    public Result<Long> apiDeleteRule(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.ofFail(-1, "id cannot be null");
        }

        ParamFlowRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "Rule not exists, id=" + id);
        }
        String app = oldEntity.getApp();

        lock.lock();
        try {
            List<ParamFlowRuleEntity> rules = ruleProvider.getRules(app);

            rules.removeIf(r -> r.getId().equals(id));
            rulePublisher.publish(app, rules);
            repository.delete(id);
        } catch (Throwable throwable) {
            logger.error("Error when deleting parameter flow rule from Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(id);
    }

    /**
     * This check method is adapted from the V1 controller.
     */
    private <R> Result<R> checkEntityInternal(ParamFlowRuleEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "bad rule body");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getResource())) {
            return Result.ofFail(-1, "resource name cannot be null or empty");
        }
        if (entity.getCount() < 0) {
            return Result.ofFail(-1, "count should be valid");
        }
        if (entity.getGrade() != RuleConstant.FLOW_GRADE_QPS) {
            return Result.ofFail(-1, "grade must be QPS mode (1)");
        }
        if (entity.getParamIdx() == null || entity.getParamIdx() < 0) {
            return Result.ofFail(-1, "paramIdx should be valid");
        }
        return null;
    }
}
