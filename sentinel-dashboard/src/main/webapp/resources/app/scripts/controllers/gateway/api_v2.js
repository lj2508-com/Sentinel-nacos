var app = angular.module('sentinelDashboardApp');

app.controller('GatewayApiCtlV2', ['$scope', '$stateParams', 'GatewayApiServiceV2', 'ngDialog',
  function ($scope, $stateParams, GatewayApiService, ngDialog) {
    $scope.app = $stateParams.app;

    $scope.apisPageConfig = {
      pageSize: 10,
      currentPageIndex: 1,
      totalPage: 1,
      totalCount: 0,
    };

    // V2 版本已移除单机相关的 macsInputConfig

    function getApis() {
      // V2 修正: 调用 V2 Service 的 queryApisForApp 方法
      GatewayApiService.queryApisForApp($scope.app).success(
          function (data) {
            if (data.code == 0 && data.data) {
              // 保留处理 predicateItems 的核心逻辑
              $scope.apis = [];
              data.data.forEach(function(api) {
                api["predicateItems"].forEach(function (item, index) {
                  var newItem = {};
                  newItem["id"] = api["id"];
                  newItem["app"] = api["app"];
                  newItem["apiName"] = api["apiName"];
                  newItem["pattern"] = item["pattern"];
                  newItem["matchStrategy"] = item["matchStrategy"];
                  newItem["itemSize"] = api["predicateItems"].length;
                  newItem["firstFlag"] = index == 0 ? 0 : 1;
                  newItem["predicateItems"] = api["predicateItems"];
                  $scope.apis.push(newItem);
                });
              });
              $scope.apisPageConfig.totalCount = data.data.length;
            } else {
              $scope.apis = [];
              $scope.apisPageConfig.totalCount = 0;
            }
          });
    };
    $scope.getApis = getApis;

    getApis();

    var gatewayApiDialog;
    $scope.editApi = function (api) {
      $scope.currentApi = angular.copy(api);
      $scope.gatewayApiDialog = {
        title: '编辑自定义 API',
        type: 'edit',
        confirmBtnText: '保存'
      };
      gatewayApiDialog = ngDialog.open({
        template: '/app/views/dialog/gateway/api-dialog.html',
        width: 900,
        overlay: true,
        scope: $scope
      });
    };

    $scope.addNewApi = function () {
      $scope.currentApi = {
        grade: 0,
        app: $scope.app,
        predicateItems: [{matchStrategy: 0, pattern: ''}]
      };
      $scope.gatewayApiDialog = {
        title: '新增自定义 API',
        type: 'add',
        confirmBtnText: '新增'
      };
      gatewayApiDialog = ngDialog.open({
        template: '/app/views/dialog/gateway/api-dialog.html',
        width: 900,
        overlay: true,
        scope: $scope
      });
    };

    $scope.saveApi = function () {
      var apiNames = [];
      if ($scope.gatewayApiDialog.type === 'add') {
        apiNames = $scope.apis.map(function (item) {
          return item["apiName"];
        }).filter(function (item, index, array) {
          return array.indexOf(item) === index;
        });
      }

      // V2 修正: 调用 checkRuleValid
      if (!GatewayApiService.checkRuleValid($scope.currentApi, apiNames)) {
        return;
      }

      if ($scope.gatewayApiDialog.type === 'add') {
        addNewApi($scope.currentApi);
      } else if ($scope.gatewayApiDialog.type === 'edit') {
        saveApi($scope.currentApi);
      }
    };

    function addNewApi(api) {
      // V2 修正: 调用 newRule
      GatewayApiService.newRule(api).success(function (data) {
        if (data.code == 0) {
          getApis();
          gatewayApiDialog.close();
        } else {
          alert('失败!');
        }
      });
    };

    function saveApi(api) {
      // V2 修正: 调用 saveRule
      GatewayApiService.saveRule(api).success(function (data) {
        if (data.code == 0) {
          getApis();
          gatewayApiDialog.close();
        } else {
          alert('失败!');
        }
      });
    };

    var confirmDialog;
    $scope.deleteApi = function (api) {
      $scope.currentApi = api;
      $scope.confirmDialog = {
        title: '删除自定义API',
        type: 'delete_api',
        attentionTitle: '请确认是否删除如下自定义API',
        attention: 'API名称: ' + api.apiName,
        confirmBtnText: '删除',
      };
      confirmDialog = ngDialog.open({
        template: '/app/views/dialog/confirm-dialog.html',
        scope: $scope,
        overlay: true
      });
    };

    $scope.confirm = function () {
      if ($scope.confirmDialog.type == 'delete_api') {
        deleteApi($scope.currentApi);
      } else {
        console.error('error');
      }
    };

    function deleteApi(api) {
      // V2 修正: 调用 deleteRule
      GatewayApiService.deleteRule(api).success(function (data) {
        if (data.code == 0) {
          getApis();
          confirmDialog.close();
        } else {
          alert('失败!');
        }
      });
    };

    $scope.addNewMatchPattern = function() {
      var total;
      if ($scope.currentApi.predicateItems == null) {
        $scope.currentApi.predicateItems = [];
        total = 0;
      } else {
        total = $scope.currentApi.predicateItems.length;
      }
      $scope.currentApi.predicateItems.splice(total + 1, 0, {matchStrategy: 0, pattern: ''});
    };

    $scope.removeMatchPattern = function($index) {
      if ($scope.currentApi.predicateItems.length <= 1) {
        alert('至少有一个匹配规则');
        return;
      }
      $scope.currentApi.predicateItems.splice($index, 1);
    };
  }]
);