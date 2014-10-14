/* global UTILS */
'use strict';

angular.module('alienUiApp').controller('ApplicationDeploymentCtrl', ['$scope', 'alienAuthService', '$upload', 'applicationServices', 'topologyServices',
  '$resource', '$http', '$q', '$translate', 'application', 'topologyId', 'environment', 'applicationEventServices', '$state',
  function($scope, alienAuthService, $upload, applicationServices, topologyServices, $resource, $http, $q, $translate, applicationResult, topologyId, environment, applicationEventServices, $state) {
    $scope.application = applicationResult.data;
    $scope.environment = environment;
    $scope.topologyId = topologyId;
    $scope.isManager = alienAuthService.hasResourceRole($scope.application, 'APPLICATION_MANAGER');
    $scope.isDeployer = alienAuthService.hasResourceRole($scope.application, 'DEPLOYMENT_MANAGER');
    $scope.isDevops = alienAuthService.hasResourceRole($scope.application, 'APPLICATION_DEVOPS');
    $scope.isUser = alienAuthService.hasResourceRole($scope.application, 'APPLICATION_USER');

    $scope.outputAttributesValue = {};
    $scope.outputPropertiesValue = {};
    $scope.validTopologyDTO = false;

    $scope.selectedComputeTemplates = {};

    $http.get('/data/mockMatcherResponse.json').success (function(data){
      $scope.matchedCloudResources = data.matchResult;
      $scope.images = data.images;
      $scope.flavors = data.flavors;
      for(var key in $scope.matchedCloudResources) {
        if($scope.matchedCloudResources.hasOwnProperty(key)) {
          var templates = $scope.matchedCloudResources[key];
          if(!$scope.selectedComputeTemplates.hasOwnProperty(key)) {
            $scope.selectedComputeTemplates[key] = templates[0];
          }
        }
      }
    });

    $scope.setCurrentMatchedComputeTemplates = function(name, currentMatchedComputeTemplates) {
      $scope.displayMatcherPannel = true;
      $scope.currentNodeTemplateId = name;
      $scope.currentMatchedComputeTemplates = currentMatchedComputeTemplates;
    };

    $scope.changeSelectedImage = function(template) {
      // TODO: send new value to server
      $scope.selectedComputeTemplates[$scope.currentNodeTemplateId] = template;
    };

    $scope.showProperty = function() {
      return $scope.deploymentPropertyDefinitions != null && $scope.deploymentPropertyDefinitions != {};
    };

    $scope.showTodoList = function() {
      return !$scope.validTopologyDTO.valid && $scope.isManager;
    };

    $scope.isSelected = function(template) {
      return template === $scope.selectedComputeTemplates[$scope.currentNodeTemplateId];
    };

    $scope.isSelectedTemplate = function(key) {
      return key === $scope.currentNodeTemplateId;
    };

    $scope.isAllowedModify = function() {
      return UTILS.isDefinedAndNotNull($scope.topologyId) && ($scope.isManager || $scope.isDevops);
    };

    $scope.isAllowedInputDeployment = function() {
      return $scope.inputPropertiesSize > 0 && ($scope.isDeployer || $scope.isManager);
    };

    $scope.isAllowedDeployment = function() {
      return $scope.isDeployer || $scope.isManager;
    };

    // get the topology and it validity status
    if ($scope.topologyId) {
      topologyServices.dao.get({
        topologyId: $scope.topologyId
      }, function(result) {
        $scope.topologyDTO = result.data;
        $scope.inputProperties = result.data.topology.inputProperties;
        $scope.outputProperties = result.data.topology.outputProperties;
        $scope.outputAttributes = result.data.topology.outputAttributes;
        $scope.inputArtifacts = result.data.topology.inputArtifacts;
        $scope.nodeTemplates = $scope.topologyDTO.topology.nodeTemplates;

        if (angular.isDefined(result.data.topology.inputProperties)) {
          $scope.inputPropertiesSize = Object.keys(result.data.topology.inputProperties).length;
        } else {
          $scope.inputPropertiesSize = 0;
        }

        if (angular.isDefined($scope.outputProperties)) {
          $scope.outputNodes = Object.keys($scope.outputProperties);
          $scope.outputPropertiesSize = Object.keys($scope.outputProperties).length;
          refreshOutputProperties();
        }

        if (angular.isDefined($scope.outputAttributes)) {
          $scope.outputNodes = UTILS.arrayUnique(UTILS.concat($scope.outputNodes, Object.keys($scope.outputAttributes)));
          $scope.outputAttributesSize = Object.keys($scope.outputAttributes).length;
        }

        if (angular.isDefined(result.data.topology.inputArtifacts)) {
          $scope.inputArtifactsSize = Object.keys(result.data.topology.inputArtifacts).length;
        } else {
          $scope.inputArtifactsSize = 0;
        }

        refreshCloudList();
        // Get the status
        refreshDeploymentStatus(false);
      });

      topologyServices.isValid({
        topologyId: $scope.topologyId
      }, function(result) {
        $scope.validTopologyDTO = result.data;
      });
    }

    var refreshOutputProperties = function() {
      for (var nodeId in $scope.outputProperties) {
        if ($scope.outputProperties.hasOwnProperty(nodeId)) {
          $scope.outputPropertiesValue[nodeId] = {};
          for (var i = 0; i < $scope.outputProperties[nodeId].length; i++) {
            var outputPropertyName = $scope.outputProperties[nodeId][i];
            $scope.outputPropertiesValue[nodeId][outputPropertyName] = $scope.nodeTemplates[nodeId].properties[outputPropertyName];
          }
        }
      }
    };

    var refreshOutputAttributes = function(appRuntimeInformation) {
      $scope.outputAttributesValue = {};
      applicationEventServices.subscribeToInstanceStateChange($state.current.name, onInstanceStateChange);
      if (UTILS.isDefinedAndNotNull(appRuntimeInformation)) {
        for (var nodeId in appRuntimeInformation) {
          if (appRuntimeInformation.hasOwnProperty(nodeId)) {
            $scope.outputAttributesValue[nodeId] = {};
            var nodeInformation = appRuntimeInformation[nodeId];
            for (var instanceId in nodeInformation) {
              if (nodeInformation.hasOwnProperty(instanceId)) {
                $scope.outputAttributesValue[nodeId][instanceId] = {};
                var allAttributes = nodeInformation[instanceId].attributes;
                for (var attribute in allAttributes) {
                  if (allAttributes.hasOwnProperty(attribute) && isOutput(nodeId, attribute, 'outputAttributes')) {
                    $scope.outputAttributesValue[nodeId][instanceId][attribute] = allAttributes[attribute];
                  }
                }
                if (Object.keys($scope.outputAttributesValue[nodeId][instanceId]).length === 0) {
                  delete $scope.outputAttributesValue[nodeId][instanceId];
                }
              }
            }
            var nbOfInstances = Object.keys($scope.outputAttributesValue[nodeId]).length;
            if (nbOfInstances === 0) {
              delete $scope.outputAttributesValue[nodeId];
            }
          }
        }
      }
    };

    var refreshInstancesStatuses = function() {
      if ($scope.outputAttributesSize > 0) {
        applicationServices.runtime.get({
          applicationId: $scope.application.id
        }, function(successResult) {
          refreshOutputAttributes(successResult.data);
        });
      }
    };

    var isOutput = function(nodeId, propertyName, type) {
      if (UTILS.isUndefinedOrNull($scope[type])) {
        return false;
      }
      if (!$scope[type].hasOwnProperty(nodeId)) {
        return false;
      }
      return $scope[type][nodeId].indexOf(propertyName) >= 0;
    };

    /**
     * Application status
     */
    var refreshDeploymentStatus = function(restart) {
      applicationEventServices.refreshApplicationStatus(function(newStatus) {
        if (restart) {
          applicationEventServices.restart();
        }
        $scope.deploymentStatus = newStatus;
        applicationEventServices.subscribeToStatusChange('application.detail.deployment', onStatusChange);
        refreshInstancesStatuses();
      });
    };

    var onInstanceStateChange = function(type, event) {
      if (UTILS.isUndefinedOrNull(event.instanceState)) {
        // Delete event
        if (UTILS.isDefinedAndNotNull($scope.outputAttributesValue[event.nodeTemplateId])) {
          delete $scope.outputAttributesValue[event.nodeTemplateId][event.instanceId];
          if (Object.keys($scope.outputAttributesValue[event.nodeTemplateId]).length === 0) {
            delete $scope.outputAttributesValue[event.nodeTemplateId];
          }
        }
      } else {
        // Add modify event
        var allAttributes = event.attributes;
        for (var attribute in allAttributes) {
          if (allAttributes.hasOwnProperty(attribute) && isOutput(event.nodeTemplateId, attribute, 'outputAttributes')) {
            if (UTILS.isUndefinedOrNull($scope.outputAttributesValue[event.nodeTemplateId])) {
              $scope.outputAttributesValue[event.nodeTemplateId] = {};
            }
            if (UTILS.isUndefinedOrNull($scope.outputAttributesValue[event.nodeTemplateId][event.instanceId])) {
              $scope.outputAttributesValue[event.nodeTemplateId][event.instanceId] = {};
            }
            $scope.outputAttributesValue[event.nodeTemplateId][event.instanceId][attribute] = allAttributes[attribute];
          }
        }
      }
      $scope.$apply();
    };

    var onStatusChange = function(type, event) {
      $scope.deploymentStatus = event.deploymentStatus;
      $scope.$apply();
    };

    $scope.$on('$destroy', function() {
      applicationEventServices.unsubscribeToStatusChange($state.current.name);
    });

    // Deployment handler
    $scope.deploy = function() {
      // Application details with deployment properties
      var deployApplicationRequest = {
        applicationId: $scope.application.id,
        deploymentProperties: $scope.deploymentProperties
      };
      $scope.isDeploying = true;
      applicationServices.deployApplication.deploy([], angular.toJson(deployApplicationRequest), function() {
        $scope.deploymentStatus = 'DEPLOYMENT_IN_PROGRESS';
        $scope.isDeploying = false;
        refreshDeploymentStatus(true);
      });
    };

    $scope.undeploy = function() {
      $scope.isUnDeploying = true;
      applicationServices.deployment.undeploy({
        applicationId: $scope.application.id
      }, function() {
        $scope.deploymentStatus = 'UNDEPLOYMENT_IN_PROGRESS';
        $scope.isUnDeploying = false;
      });
    };


    /* Handle properties inputs */
    $scope.updateProperty = function(nodeTemplateName, propertyName, propertyValue) {

      // No update if it's the same value
      if (propertyValue === $scope.nodeTemplates[nodeTemplateName].properties[propertyName]) {
        return;
      }
      var updatePropsObject = {
        'propertyName': propertyName,
        'propertyValue': propertyValue
      };

      var d = $q.defer();
      topologyServices.nodeTemplate.updateProperty({
        topologyId: $scope.topologyDTO.topology.id,
        nodeTemplateName: nodeTemplateName
      }, angular.toJson(updatePropsObject), function(data) {
        if (data.error !== null) {
          // Constraint error display + translation
          var constraintInfo = data.data;
          d.resolve($translate('ERRORS.' + data.error.code + '.' + constraintInfo.name, constraintInfo));
        } else {
          d.resolve();
          if (UTILS.isDefinedAndNotNull($scope.outputPropertiesValue[nodeTemplateName]) && UTILS.isDefinedAndNotNull($scope.outputPropertiesValue[nodeTemplateName][propertyName])) {
            $scope.outputPropertiesValue[nodeTemplateName][propertyName] = propertyValue;
          }
        }
      });
      return d.promise;
    };

    // Artifact upload handler
    $scope.doUploadArtifact = function(file, nodeTemplateName, artifactName) {
      if (UTILS.isUndefinedOrNull($scope.uploads)) {
        $scope.uploads = {};
      }
      $scope.uploads[artifactName] = {
        'isUploading': true,
        'type': 'info'
      };
      $upload.upload({
        url: 'rest/topologies/' + $scope.topologyDTO.topology.id + '/nodetemplates/' + nodeTemplateName + '/artifacts/' + artifactName,
        file: file
      }).progress(function(evt) {
        $scope.uploads[artifactName].uploadProgress = parseInt(100.0 * evt.loaded / evt.total);
      }).success(function(success) {
        $scope.nodeTemplates[nodeTemplateName].artifacts[artifactName].artifactRef = success.data;
        $scope.uploads[artifactName].isUploading = false;
        $scope.uploads[artifactName].type = 'success';
      }).error(function(data, status) {
        $scope.uploads[artifactName].type = 'error';
        $scope.uploads[artifactName].error = {};
        $scope.uploads[artifactName].error.code = status;
        $scope.uploads[artifactName].error.message = 'An Error has occurred on the server!';
      });
    };

    $scope.onArtifactSelected = function($files, nodeTemplateName, artifactName) {
      var file = $files[0];
      $scope.doUploadArtifact(file, nodeTemplateName, artifactName);
    };

    // DEPLOYMENT AND CLOUD MANAGEMENT

    var refreshDeploymentPropertyDefinitions = function() {
      if ($scope.selectedCloud) {
        $scope.deploymentProperties = {};
        $http.get('rest/clouds/' + $scope.selectedCloud.id + '/deploymentpropertydefinitions').success(function(result) {
          if (result.data) {
            $scope.deploymentPropertyDefinitions = result.data;
            for (var propertyName in $scope.deploymentPropertyDefinitions) {
              if ($scope.deploymentPropertyDefinitions.hasOwnProperty(propertyName)) {
                $scope.deploymentPropertyDefinitions[propertyName].name = propertyName;
                if (UTILS.isDefinedAndNotNull($scope.deploymentPropertyDefinitions[propertyName].defaultValue)) {
                  $scope.deploymentProperties[propertyName] = $scope.deploymentPropertyDefinitions[propertyName].defaultValue;
                } else {
                  $scope.deploymentProperties[propertyName] = null;
                }
              }
            }
          }
        });
      }
    };

    // search for clouds
    var Cloud = $resource('rest/clouds/search', {}, {});
    var refreshCloudList = function() {
      Cloud.get({
        enabledOnly: true
      }, function(result) {
        var clouds = result.data.data;
        $scope.clouds = clouds;
        if (clouds) {
          // select the cloud that is currently associated with the topology
          var found = false,
            i = 0;
          while (!found && i < clouds.length) {
            if (clouds[i].id === $scope.environment.cloudId) {
              $scope.selectedCloud = clouds[i];
              refreshDeploymentPropertyDefinitions();
              found = true;
            }
            i++;
          }
        }
      });
    };

    /** change the cloud for the topology */
    $scope.changeCloud = function(selectedCloud) {

      if (!selectedCloud) {
        // reset deployment property bloc
        $scope.deploymentPropertyDefinitions = null;
        return;
      }
      topologyServices.cloud.set({
        applicationId: $scope.application.id
      }, selectedCloud.id, function(result) {
        if (result.error === null) {
          $scope.selectedCloud = selectedCloud;
          $scope.environment.cloudId = selectedCloud.id;
          refreshDeploymentStatus(true);
          refreshDeploymentPropertyDefinitions();
        }
      });
    };

    /** Properties definition */
    $scope.updateDeploymentProperty = function(propertyDefinition, propertyValue) {
      var propertyName = propertyDefinition.name;
      if (propertyValue === $scope.deploymentProperties[propertyName]) {
        return; // no change
      }
      var deploymentPropertyObject = {
        'cloudId': $scope.selectedCloud.id,
        'deploymentPropertyName': propertyName,
        'deploymentPropertyValue': propertyValue
      };

      return applicationServices.checkProperty({}, angular.toJson(deploymentPropertyObject), function(data) {
        if (data.error === null) {
          $scope.deploymentProperties[propertyName] = propertyValue;
        }
      }).$promise;
    };
  }
]);
