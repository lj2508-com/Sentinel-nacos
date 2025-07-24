/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.v2;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.client.SentinelApiClient;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.AppManagement;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * Controller regarding APIs of degrade rules. Refactored since 1.8.0.
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */
@RestController
@RequestMapping("/v2/degrade")
public class DegradeControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(DegradeControllerV2.class);

    @Autowired
    private RuleRepository<DegradeRuleEntity, Long> repository;

    // 注入我们为 Nacos 创建的 Provider 和 Publisher
    @Autowired
    @Qualifier("degradeRuleNacosProvider")
    private DynamicRuleProvider<List<DegradeRuleEntity>> ruleProvider;

    @Autowired
    @Qualifier("degradeRuleNacosPublisher")
    private DynamicRulePublisher<List<DegradeRuleEntity>> rulePublisher;

    @Autowired
    private SentinelApiClient sentinelApiClient;
    @Autowired
    private AppManagement appManagement;

    @GetMapping("/rules.json")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<DegradeRuleEntity>> apiQueryMachineRules(String app, String ip, Integer port) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<DegradeRuleEntity> rules = ruleProvider.getRules(app);
            if (rules != null && !rules.isEmpty()) {
                repository.saveAll(rules);
            }
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying degrade rules", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        }
    }

    @PostMapping("/rule")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<DegradeRuleEntity> apiAddRule(@RequestBody DegradeRuleEntity entity) {
        // V2 改造：将规则保存到 Nacos
        Result<DegradeRuleEntity> checkResult = checkEntity(entity);
        if (checkResult != null) {
            return checkResult;
        }
        entity.setId(null); // ID由 Sentinel 内部生成
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);

        try {
            List<DegradeRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            rules.add(entity);
            repository.save(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when adding new degrade rule", throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {

        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<DegradeRuleEntity> apiUpdateRule(@PathVariable("id") Long id,
                                                     @RequestBody DegradeRuleEntity entity) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "id can't be null or negative");
        }
        DegradeRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "Degrade rule does not exist, id=" + id);
        }
        entity.setApp(oldEntity.getApp());
        entity.setIp(oldEntity.getIp());
        entity.setPort(oldEntity.getPort());
        entity.setId(oldEntity.getId());
        // V2 改造：更新规则并保存到 Nacos
        Result<DegradeRuleEntity> checkResult = checkEntity(entity);
        if (checkResult != null) {
            return checkResult;
        }
        entity.setId(id);
        Date date = new Date();
        entity.setGmtCreate(null);
        entity.setGmtModified(date);

        try {
            List<DegradeRuleEntity> rules = ruleProvider.getRules(entity.getApp());
            // 移除旧规则，添加新规则'
            rules.removeIf(rule -> rule.getResource().equals(entity.getResource()));
            rules.add(entity);
            repository.save(entity);
            rulePublisher.publish(entity.getApp(), rules);
        } catch (Throwable throwable) {
            logger.error("Error when updating degrade rule, id=" + id, throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {

        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<Long> delete(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }

        // 1. 通过 ID 先从内存仓库中找到规则实体
        DegradeRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "Rule not exists, id=" + id);
        }
        // 2. 从找到的实体中获取 app 名称
        String app = oldEntity.getApp();

        try {
            // 3. 从 Nacos 中获取该 app 的全量规则
            List<DegradeRuleEntity> rules = ruleProvider.getRules(app);
            // 4. 移除要删除的规则
            rules.removeIf(rule -> rule.getId().equals(id));
            // 5. 将修改后的全量规则发布回 Nacos
            rulePublisher.publish(app, rules);
        } catch (Throwable throwable) {
            logger.error("Error when deleting degrade rule, id=" + id, throwable);
            return Result.ofFail(-1, throwable.getMessage());
        } finally {
        }

        // 同时，也从内存仓库中删除
        repository.delete(id);

        return Result.ofSuccess(id);
    }



    private <R> Result<R> checkEntity(DegradeRuleEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "entity can't be null");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be blank");
        }
        if (StringUtil.isBlank(entity.getLimitApp())) {
            return Result.ofFail(-1, "limitApp can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getResource())) {
            return Result.ofFail(-1, "resource can't be blank");
        }
        if (entity.getGrade() == null) {
            return Result.ofFail(-1, "grade can't be null");
        }
        if (entity.getGrade() < 0 || entity.getGrade() > 2) {
            return Result.ofFail(-1, "Invalid grade: " + entity.getGrade());
        }
        if (entity.getCount() == null || entity.getCount() < 0) {
            return Result.ofFail(-1, "count can't be negative");
        }
        if (entity.getTimeWindow() == null || entity.getTimeWindow() <= 0) {
            return Result.ofFail(-1, "timeWindow should be positive");
        }
        if (entity.getMinRequestAmount() == null || entity.getMinRequestAmount() <= 0) {
            return Result.ofFail(-1, "minRequestAmount should be positive");
        }
        if (entity.getStatIntervalMs() == null || entity.getStatIntervalMs() <= 0) {
            return Result.ofFail(-1, "statIntervalMs should be positive");
        }
        if (entity.getGrade() == 0) { // Slow-request ratio
            if (entity.getSlowRatioThreshold() == null || entity.getSlowRatioThreshold() < 0 || entity.getSlowRatioThreshold() > 1) {
                return Result.ofFail(-1, "slowRatioThreshold should be in [0.0, 1.0]");
            }
        }
        return null;
    }
}
