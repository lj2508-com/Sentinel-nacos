/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.gateway.v2;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.GatewayFlowRuleEntity;
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

/**
 * Gateway flow rule controller V2.
 */
@RestController
@RequestMapping(value = "/v2/gateway/flow")
public class GatewayFlowRuleControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(GatewayFlowRuleControllerV2.class);

    @Autowired
    private RuleRepository<GatewayFlowRuleEntity, Long> repository;

    // V2 改造：注入 Nacos 的规则提供者和发布者
    @Autowired
    @Qualifier("gatewayFlowRuleNacosProvider")
    private DynamicRuleProvider<List<GatewayFlowRuleEntity>> ruleProvider;
    @Autowired
    @Qualifier("gatewayFlowRuleNacosPublisher")
    private DynamicRulePublisher<List<GatewayFlowRuleEntity>> rulePublisher;

    /**
     * V2 改造：查询应用的全部网关流控规则
     */
    @GetMapping("/rules")
    @AuthAction(AuthService.PrivilegeType.READ_RULE)
    public Result<List<GatewayFlowRuleEntity>> apiQueryRulesForApp(@RequestParam String app) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<GatewayFlowRuleEntity> rules = ruleProvider.getRules(app);
            repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying gateway flow rules", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    /**
     * V2 改造：检查实体时，不再检查ip和port
     */
    private <R> Result<R> checkEntityInternal(GatewayFlowRuleEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "bad rule body");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (entity.getResource() == null) {
            return Result.ofFail(-1, "resource can't be null");
        }
        if (entity.getCount() == null || entity.getCount() < 0) {
            return Result.ofFail(-1, "count should be valid");
        }
        if (entity.getGrade() == null || (entity.getGrade() != 0 && entity.getGrade() != 1) ) {
            return Result.ofFail(-1, "grade should be valid");
        }
        // 您可以根据 GatewayFlowRuleEntity 的具体字段添加更详细的校验
        return null;
    }

    @PostMapping("/rule")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<GatewayFlowRuleEntity> apiAddRule(@RequestBody GatewayFlowRuleEntity entity) {
        Result<GatewayFlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }
        entity.setId(null);
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("Failed to add gateway flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<GatewayFlowRuleEntity> apiUpdateRule(@PathVariable("id") Long id,
                                                       @RequestBody GatewayFlowRuleEntity entity) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        GatewayFlowRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "id " + id + " does not exist");
        }

        Result<GatewayFlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        entity.setId(id);
        Date date = new Date();
        entity.setGmtCreate(oldEntity.getGmtCreate());
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("Failed to update gateway flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.DELETE_RULE)
    public Result<Long> apiDeleteRule(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        GatewayFlowRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }

        try {
            repository.delete(id);
            publishRules(oldEntity.getApp());
        } catch (Throwable throwable) {
            logger.error("Failed to delete gateway flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(id);
    }

    /**
     * V2 改造：发布规则到配置中心（如Nacos）
     */
    private void publishRules(String app) throws Exception {
        List<GatewayFlowRuleEntity> rules = repository.findAllByApp(app);
        rulePublisher.publish(app, rules);
    }
}