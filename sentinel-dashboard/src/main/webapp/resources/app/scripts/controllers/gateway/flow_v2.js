var app = angular.module('sentinelDashboardApp');

// V2 改造：控制器重命名为 GatewayFlowControllerV2
app.controller('GatewayFlowCtlV2', ['$scope', '$stateParams', 'GatewayFlowServiceV2', 'GatewayApiServiceV2', 'ngDialog',
    // V2 改造：注入 V2 版本的服务
    function ($scope, $stateParams, GatewayFlowService, GatewayApiService, ngDialog) {
        $scope.app = $stateParams.app;

        $scope.rulesPageConfig = {
            pageSize: 10,
            currentPageIndex: 1,
            totalPage: 1,
            totalCount: 0,
        };

        // V2 改造：移除单机相关的 macsInputConfig

        // V2 改造：函数重命名并修改逻辑，不再依赖单机
        function getRules() {
            // 直接调用 V2 Service 中查询应用规则的方法
            GatewayFlowService.queryRulesForApp($scope.app).success(
                function (data) {
                    if (data.code == 0 && data.data) {
                        $scope.rules = data.data;
                        $scope.rulesPageConfig.totalCount = $scope.rules.length;
                    } else {
                        $scope.rules = [];
                        $scope.rulesPageConfig.totalCount = 0;
                    }
                });
        };
        $scope.getRules = getRules;

        // V2 改造：函数重命名并修改逻辑，不再依赖单机
        function getApiNames() {
            // 假设 GatewayApiServiceV2 提供了按 app 查询的方法
            GatewayApiService.queryApisForApp($scope.app).success(
                function (data) {
                    if (data.code == 0 && data.data) {
                        $scope.apiNames = [];
                        data.data.forEach(function (api) {
                            $scope.apiNames.push(api["apiName"]);
                        });
                    }
                });
        }

        // 初始化时直接获取应用维度的规则和API分组
        getRules();
        getApiNames();

        $scope.intervalUnits = [{val: 0, desc: '秒'}, {val: 1, desc: '分'}, {val: 2, desc: '时'}, {val: 3, desc: '天'}];

        var gatewayFlowRuleDialog;
        $scope.editRule = function (rule) {
            $scope.currentRule = angular.copy(rule);
            $scope.gatewayFlowRuleDialog = {
                title: '编辑网关流控规则',
                type: 'edit',
                confirmBtnText: '保存'
            };
            gatewayFlowRuleDialog = ngDialog.open({
                template: '/app/views/dialog/gateway/flow-rule-dialog.html',
                width: 780,
                overlay: true,
                scope: $scope
            });
        };

        $scope.addNewRule = function () {
            // V2 改造：新增规则时不再需要 ip 和 port
            $scope.currentRule = {
                grade: 1,
                app: $scope.app,
                // ip: mac[0],
                // port: mac[1],
                resourceMode: 0,
                interval: 1,
                intervalUnit: 0,
                controlBehavior: 0,
                burst: 0,
                maxQueueingTimeoutMs: 0
            };

            $scope.gatewayFlowRuleDialog = {
                title: '新增网关流控规则',
                type: 'add',
                confirmBtnText: '新增'
            };

            gatewayFlowRuleDialog = ngDialog.open({
                template: '/app/views/dialog/gateway/flow-rule-dialog.html',
                width: 780,
                overlay: true,
                scope: $scope
            });
        };

        $scope.saveRule = function () {
            if (!GatewayFlowService.checkRuleValid($scope.currentRule)) {
                return;
            }
            if ($scope.gatewayFlowRuleDialog.type === 'add') {
                addNewRule($scope.currentRule);
            } else if ($scope.gatewayFlowRuleDialog.type === 'edit') {
                saveRule($scope.currentRule);
            }
        };

        // 以下辅助函数保持不变
        $scope.useRouteID = function() { $scope.currentRule.resource = ''; };
        $scope.useCustormAPI = function() { $scope.currentRule.resource = ''; };
        $scope.useParamItem = function () { $scope.currentRule.paramItem = { parseStrategy: 0, matchStrategy: 0 }; };
        $scope.notUseParamItem = function () { $scope.currentRule.paramItem = null; };
        $scope.useParamItemVal = function() { $scope.currentRule.paramItem.pattern = ""; $scope.currentRule.paramItem.matchStrategy = 0; };
        $scope.notUseParamItemVal = function() { $scope.currentRule.paramItem.pattern = null; $scope.currentRule.paramItem.matchStrategy = null; };

        function addNewRule(rule) {
            GatewayFlowService.newRule(rule).success(function (data) {
                if (data.code == 0) {
                    getRules();
                    gatewayFlowRuleDialog.close();
                } else {
                    // V2 改造：简化错误提示
                    alert('失败!');
                }
            });
        };

        function saveRule(rule) {
            GatewayFlowService.saveRule(rule).success(function (data) {
                if (data.code == 0) {
                    getRules();
                    gatewayFlowRuleDialog.close();
                } else {
                    // V2 改造：简化错误提示
                    alert('失败!');
                }
            });
        };

        var confirmDialog;
        $scope.deleteRule = function (rule) {
            $scope.currentRule = rule;
            $scope.confirmDialog = {
                title: '删除网关流控规则',
                type: 'delete_rule',
                attentionTitle: '请确认是否删除如下规则',
                attention: 'API名称: ' + rule.resource + ', ' + (rule.grade == 1 ? 'QPS阈值' : '线程数') + ': ' + rule.count,
                confirmBtnText: '删除',
            };
            confirmDialog = ngDialog.open({
                template: '/app/views/dialog/confirm-dialog.html',
                scope: $scope,
                overlay: true
            });
        };

        $scope.confirm = function () {
            if ($scope.confirmDialog.type == 'delete_rule') {
                deleteRule($scope.currentRule);
            } else {
                console.error('error');
            }
        };

        function deleteRule(rule) {
            GatewayFlowService.deleteRule(rule).success(function (data) {
                if (data.code == 0) {
                    getRules();
                    confirmDialog.close();
                } else {
                    // V2 改造：简化错误提示
                    alert('失败!');
                }
            });
        };

        // V2 改造：移除 queryAppMachines 和 $watch
    }]
);