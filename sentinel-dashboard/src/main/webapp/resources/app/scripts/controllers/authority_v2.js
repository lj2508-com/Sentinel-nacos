/**
 * Authority rule controller V2.
 */
// V2 改造：控制器重命名为 AuthorityRuleControllerV2
angular.module('sentinelDashboardApp').controller('AuthorityRuleControllerV2', ['$scope', '$stateParams', 'AuthorityRuleServiceV2', 'ngDialog',
    'MachineService',
    // V2 改造：注入 AuthorityRuleServiceV2
    function ($scope, $stateParams, AuthorityRuleService, ngDialog,
              MachineService) {
        $scope.app = $stateParams.app;

        $scope.rulesPageConfig = {
            pageSize: 10,
            currentPageIndex: 1,
            totalPage: 1,
            totalCount: 0,
        };
        $scope.macsInputConfig = {
            searchField: ['text', 'value'],
            persist: true,
            create: false,
            maxItems: 1,
            render: {
                item: function (data, escape) {
                    return '<div>' + escape(data.text) + '</div>';
                }
            },
            onChange: function (value, oldValue) {
                $scope.macInputModel = value;
            }
        };

        function getMachineRules() {
            if (!$scope.macInputModel) {
                return;
            }
            let mac = $scope.macInputModel.split(':');
            AuthorityRuleService.queryMachineRules($scope.app, mac[0], mac[1])
                .success(function (data) {
                    // V2 改造：统一使用 data.code 作为成功判断条件
                    if (data.code === 0 && data.data) {
                        $scope.loadError = undefined;
                        $scope.rules = data.data;
                        $scope.rulesPageConfig.totalCount = $scope.rules.length;
                    } else {
                        $scope.rules = [];
                        $scope.rulesPageConfig.totalCount = 0;
                        $scope.loadError = {message: data.msg};
                    }
                })
                .error((data, header, config, status) => {
                    $scope.loadError = {message: "未知错误"};
                });
        };
        $scope.getMachineRules = getMachineRules;
        getMachineRules();

        var authorityRuleDialog;

        $scope.editRule = function (rule) {
            $scope.currentRule = angular.copy(rule);
            $scope.authorityRuleDialog = {
                title: '编辑授权规则',
                type: 'edit',
                confirmBtnText: '保存',
            };
            authorityRuleDialog = ngDialog.open({
                template: '/app/views/dialog/authority-rule-dialog.html',
                width: 680,
                overlay: true,
                scope: $scope
            });
        };

        $scope.addNewRule = function () {
            var mac = $scope.macInputModel.split(':');
            $scope.currentRule = {
                app: $scope.app,
                ip: mac[0],
                port: mac[1],
                rule: {
                    strategy: 0,
                    limitApp: '',
                }
            };
            $scope.authorityRuleDialog = {
                title: '新增授权规则',
                type: 'add',
                confirmBtnText: '新增'
            };
            authorityRuleDialog = ngDialog.open({
                template: '/app/views/dialog/authority-rule-dialog.html',
                width: 680,
                overlay: true,
                scope: $scope
            });
        };

        $scope.saveRule = function () {
            // 此处 AuthorityRuleService.checkRuleValid 假设已在 V2 服务中提供
            if (!AuthorityRuleService.checkRuleValid($scope.currentRule.rule)) {
                return;
            }
            if ($scope.authorityRuleDialog.type === 'add') {
                addNewRule($scope.currentRule);
            } else if ($scope.authorityRuleDialog.type === 'edit') {
                saveRule($scope.currentRule);
            }
        };

        // V2 改造：函数名简化，处理逻辑简化
        function addNewRule(rule) {
            AuthorityRuleService.addNewRule(rule).success((data) => {
                if (data.code == 0) {
                    getMachineRules();
                    authorityRuleDialog.close();
                } else {
                    alert('失败!');
                }
            }).error(() => {
                alert('失败!');
            });
        }

        // V2 改造：函数名简化，处理逻辑简化
        function saveRule(rule) {
            AuthorityRuleService.saveRule(rule).success(function (data) {
                if (data.code == 0) {
                    getMachineRules();
                    authorityRuleDialog.close();
                } else {
                    alert('失败!');
                }
            }).error(() => {
                alert('失败!');
            });
        }

        // V2 改造：函数名简化，处理逻辑简化
        function deleteRule(rule) {
            AuthorityRuleService.deleteRule(rule).success((data) => {
                if (data.code == 0) {
                    getMachineRules();
                    confirmDialog.close();
                } else {
                    alert('失败!');
                }
            }).error(() => {
                alert('失败!');
            });
        };

        var confirmDialog;
        $scope.deleteRule = function (ruleEntity) {
            $scope.currentRule = ruleEntity;
            $scope.confirmDialog = {
                title: '删除授权规则',
                type: 'delete_rule',
                attentionTitle: '请确认是否删除如下授权限流规则',
                attention: '资源名: ' + ruleEntity.rule.resource + ', 流控应用: ' + ruleEntity.rule.limitApp +
                    ', 类型: ' + (ruleEntity.rule.strategy === 0 ? '白名单' : '黑名单'),
                confirmBtnText: '删除',
            };
            confirmDialog = ngDialog.open({
                template: '/app/views/dialog/confirm-dialog.html',
                scope: $scope,
                overlay: true
            });
        };

        $scope.confirm = function () {
            if ($scope.confirmDialog.type === 'delete_rule') {
                // V2 改造：调用简化后的函数名
                deleteRule($scope.currentRule);
            } else {
                console.error('error');
            }
        };

        queryAppMachines();

        function queryAppMachines() {
            MachineService.getAppMachines($scope.app).success(
                function (data) {
                    if (data.code == 0) {
                        if (data.data) {
                            $scope.machines = [];
                            $scope.macsInputOptions = [];
                            data.data.forEach(function (item) {
                                if (item.healthy) {
                                    $scope.macsInputOptions.push({
                                        text: item.ip + ':' + item.port,
                                        value: item.ip + ':' + item.port
                                    });
                                }
                            });
                        }
                        if ($scope.macsInputOptions.length > 0) {
                            $scope.macInputModel = $scope.macsInputOptions[0].value;
                        }
                    } else {
                        $scope.macsInputOptions = [];
                    }
                }
            );
        };
        $scope.$watch('macInputModel', function () {
            if ($scope.macInputModel) {
                getMachineRules();
            }
        });
    }]);