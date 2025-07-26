var app = angular.module('sentinelDashboardApp');

// V2 改造：控制器重命名为 SystemControllerV2
app.controller('SystemControllerV2', ['$scope', '$stateParams', 'SystemServiceV2', 'ngDialog', 'MachineService',
  // V2 改造：注入 SystemServiceV2
  function ($scope, $stateParams, SystemService,
            ngDialog, MachineService) {
    //初始化
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

    getMachineRules();
    function getMachineRules() {
      if (!$scope.macInputModel) {
        return;
      }
      let mac = $scope.macInputModel.split(':');
      SystemService.queryMachineRules($scope.app, mac[0], mac[1]).success(
          function (data) {
            if (data.code == 0 && data.data) {
              var rules = data.data;

              // V2 最终修正：在前端根据阈值情况，为规则对象补上 grade 字段
              // 这是解决编辑框不显示输入框的关键
              rules.forEach(function(rule) {
                if (rule.highestSystemLoad >= 0) {
                  rule.grade = 0;
                } else if (rule.avgRt >= 0) {
                  rule.grade = 1;
                } else if (rule.maxThread >= 0) {
                  rule.grade = 2;
                } else if (rule.qps >= 0) {
                  rule.grade = 3;
                } else if (rule.highestCpuUsage >= 0) {
                  rule.grade = 4;
                }
              });

              $scope.rules = rules;
              $scope.rulesPageConfig.totalCount = $scope.rules.length;
            } else {
              $scope.rules = [];
              $scope.rulesPageConfig.totalCount = 0;
            }
          });
    }

    $scope.getMachineRules = getMachineRules;
    var systemRuleDialog;
    $scope.editRule = function (rule) {
      $scope.currentRule = angular.copy(rule);
      $scope.systemRuleDialog = {
        title: '编辑系统保护规则',
        type: 'edit',
        confirmBtnText: '保存'
      };
      systemRuleDialog = ngDialog.open({
        template: '/app/views/dialog/system-rule-dialog.html',
        width: 680,
        overlay: true,
        scope: $scope
      });
    };

    $scope.addNewRule = function () {
      var mac = $scope.macInputModel.split(':');
      $scope.currentRule = {
        grade: 0,
        app: $scope.app,
        ip: mac[0],
        port: mac[1],
        // V2 改造：参考flow_v2.js，为新增规则提供更完善的默认阈值
        highestSystemLoad: 0,
        avgRt: 0,
        maxThread: 0,
        qps: 0,
        highestCpuUsage: 0,
      };
      $scope.systemRuleDialog = {
        title: '新增系统保护规则',
        type: 'add',
        confirmBtnText: '新增'
      };
      systemRuleDialog = ngDialog.open({
        template: '/app/views/dialog/system-rule-dialog.html',
        width: 680,
        overlay: true,
        scope: $scope
      });
    };

    $scope.saveRule = function () {
      // V2 改造：参考flow_v2.js，在保存前增加规则校验
      if (!SystemService.checkRuleValid($scope.currentRule)) {
        return;
      }
      if ($scope.systemRuleDialog.type === 'add') {
        addNewRule($scope.currentRule);
      } else if ($scope.systemRuleDialog.type === 'edit') {
        saveRule($scope.currentRule, true);
      }
    };

    // V2 改造：提供辅助函数，简化视图和确认对话框的逻辑
    $scope.getRuleTypeDesc = function(rule) {
      switch(rule.grade) {
        case 0: return "LOAD";
        case 1: return "RT";
        case 2: return "线程数";
        case 3: return "QPS";
        case 4: return "CPU 使用率";
        default: return "未知";
      }
    };
    $scope.getRuleThreshold = function(rule) {
      switch(rule.grade) {
        case 0: return rule.highestSystemLoad;
        case 1: return rule.avgRt;
        case 2: return rule.maxThread;
        case 3: return rule.qps;
        case 4: return rule.highestCpuUsage;
        default: return "未知";
      }
    };

    var confirmDialog;
    $scope.deleteRule = function (rule) {
      $scope.currentRule = rule;
      $scope.confirmDialog = {
        title: '删除系统保护规则',
        type: 'delete_rule',
        attentionTitle: '请确认是否删除如下系统保护规则',
        // V2 改造：使用辅助函数简化attention文本的生成
        attention: '阈值类型: ' + $scope.getRuleTypeDesc(rule) + ', 阈值: ' + $scope.getRuleThreshold(rule),
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
        deleteRule($scope.currentRule);
      } else {
        console.error('error');
      }
    };

    function deleteRule(rule) {
      SystemService.deleteRule(rule).success(function (data) {
        // V2 改造：简化成功/失败处理逻辑
        if (data.code == 0) {
          getMachineRules();
          confirmDialog.close();
        } else {
          alert('失败!');
        }
      });
    }

    function addNewRule(rule) {
      SystemService.newRule(rule).success(function (data) {
        // V2 改造：简化成功/失败处理逻辑
        if (data.code == 0) {
          getMachineRules();
          systemRuleDialog.close();
        } else {
          alert('失败!');
        }
      });
    }

    function saveRule(rule, edit) {
      SystemService.saveRule(rule).success(function (data) {
        // V2 改造：简化成功/失败处理逻辑
        if (data.code == 0) {
          getMachineRules();
          if (edit) {
            systemRuleDialog.close();
          } else {
            confirmDialog.close();
          }
        } else {
          alert('失败!');
        }
      });
    }

    queryAppMachines();
    function queryAppMachines() {
      MachineService.getAppMachines($scope.app).success(
          function (data) {
            if (data.code === 0) {
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