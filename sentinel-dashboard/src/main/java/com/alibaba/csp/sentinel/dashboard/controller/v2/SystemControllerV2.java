package com.alibaba.csp.sentinel.dashboard.controller.v2;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.SystemRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controller for System Rule (V2), for Nacos persistence.
 */
@RestController
@RequestMapping("/v2/system")
public class SystemControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(SystemControllerV2.class);

    @Autowired
    private RuleRepository<SystemRuleEntity, Long> repository;

    @Autowired
    @Qualifier("systemRuleNacosProvider")
    private DynamicRuleProvider<List<SystemRuleEntity>> ruleProvider;

    @Autowired
    @Qualifier("systemRuleNacosPublisher")
    private DynamicRulePublisher<List<SystemRuleEntity>> rulePublisher;

    private final ReentrantLock lock = new ReentrantLock();

    @GetMapping("/rules")
    public Result<List<SystemRuleEntity>> apiQueryRules(String app) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<SystemRuleEntity> rules = ruleProvider.getRules(app);
            repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying system rules from Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        }
    }

    @PostMapping("/rule")
    public Result<SystemRuleEntity> apiAddRule(@RequestBody SystemRuleEntity entity) {
        Result<SystemRuleEntity> checkResult = checkEntity(entity);
        if (Objects.nonNull(checkResult)) {
            return checkResult;
        }
        entity.setId(null);
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);

        fillDefaultValues(entity);

        lock.lock();
        try {
            entity = repository.save(entity);
            List<SystemRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            rules.add(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when adding new system rule to Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    public Result<SystemRuleEntity> apiUpdateRule(@PathVariable Long id, @RequestBody SystemRuleEntity entity) {
        if (Objects.isNull(id)) {
            return Result.ofFail(-1, "id can't be null");
        }
        Result<SystemRuleEntity> checkResult = checkEntity(entity);
        if (Objects.nonNull(checkResult)) {
            return checkResult;
        }
        entity.setId(id);
        Date date = new Date();
        entity.setGmtCreate(null);
        entity.setGmtModified(date);

        fillDefaultValues(entity);

        lock.lock();
        try {
            List<SystemRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            rules.removeIf(r -> r.getId().equals(id));
            rules.add(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when updating system rule in Nacos, id=" + id, throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    public Result<Long> apiDeleteRule(@PathVariable Long id) {
        if (Objects.isNull(id)) {
            return Result.ofFail(-1, "id can't be null");
        }
        SystemRuleEntity oldEntity = repository.findById(id);
        if (Objects.isNull(oldEntity)) {
            return Result.ofFail(-1, "Rule not exists, id=" + id);
        }
        String app = oldEntity.getApp();

        lock.lock();
        try {
            List<SystemRuleEntity> rules = ruleProvider.getRules(app);
            rules.removeIf(r -> r.getId().equals(id));
            rulePublisher.publish(app, rules);
            repository.delete(id);
        } catch (Throwable throwable) {
            logger.error("Error when deleting system rule from Nacos", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
            lock.unlock();
        }
        return Result.ofSuccess(id);
    }

    private <R> Result<R> checkEntity(SystemRuleEntity entity) {
        if (Objects.isNull(entity)) {
            return Result.ofFail(-1, "entity can't be null");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be blank");
        }

        int notNullCount = countNotNullAndNotNegative(entity.getHighestSystemLoad(), entity.getHighestCpuUsage(),
                entity.getAvgRt(), entity.getMaxThread(), entity.getQps());

        if (notNullCount > 1 || notNullCount < 1) {
            return Result.ofFail(-1, "only one of [highestSystemLoad, highestCpuUsage, avgRt, maxThread, qps] "
                    + "value must be set and not negative, but " + notNullCount + " values get");
        }

        if (Objects.nonNull(entity.getHighestCpuUsage()) && entity.getHighestCpuUsage() > 1) {
            return Result.ofFail(-1, "highestCpuUsage must be between [0.0, 1.0]");
        }
        return null;
    }

    private int countNotNullAndNotNegative(Number... values) {
        int notNullCount = 0;
        for (Number value : values) {
            if (Objects.nonNull(value)) {
                if (!(value.doubleValue() < 0)) {
                    notNullCount++;
                }
            }
        }
        return notNullCount;
    }

    private void fillDefaultValues(SystemRuleEntity entity) {
        if (Objects.isNull(entity.getHighestSystemLoad())) {
            entity.setHighestSystemLoad(-1D);
        }
        if (Objects.isNull(entity.getHighestCpuUsage())) {
            entity.setHighestCpuUsage(-1D);
        }
        if (Objects.isNull(entity.getAvgRt())) {
            entity.setAvgRt(-1L);
        }
        if (Objects.isNull(entity.getMaxThread())) {
            entity.setMaxThread(-1L);
        }
        if (Objects.isNull(entity.getQps())) {
            entity.setQps(-1D);
        }
    }
}