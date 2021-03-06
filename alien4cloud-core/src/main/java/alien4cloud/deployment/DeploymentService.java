package alien4cloud.deployment;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.deployment.DeploymentTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.utils.MapUtil;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manage deployment operations on a cloud.
 */
@Service
@Slf4j
public class DeploymentService {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDao;
    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;
    @Inject
    private DeploymentRuntimeStateService deploymentRuntimeStateService;
    @Inject
    private DeploymentContextService deploymentContextService;
    @Inject
    private DeploymentTopologyService deploymentTopologyService;

    /**
     * Get all deployments for a given orchestrator an application
     *
     * @param orchestratorId Id of the cloud for which to get deployments (can be null to get deployments for all clouds).
     * @param sourceId Id of the application for which to get deployments (can be null to get deployments for all applications).
     * @return A {@link GetMultipleDataResult} that contains deployments.
     */
    public List<Deployment> getDeployments(String orchestratorId, String sourceId) {
        QueryBuilder query = QueryBuilders.boolQuery();
        if (orchestratorId != null) {
            query = QueryBuilders.boolQuery().must(QueryBuilders.termsQuery("orchestratorId", orchestratorId));
        }
        if (sourceId != null) {
            query = QueryBuilders.boolQuery().must(query).must(QueryBuilders.termsQuery("sourceId", sourceId));
        }
        if (orchestratorId == null && sourceId == null) {
            query = QueryBuilders.matchAllQuery();
        }
        return alienDao.customFindAll(Deployment.class, query);
    }

    /**
     * Get a deployment given its id
     *
     * @param id id of the deployment
     * @return deployment with given id
     */
    public Deployment get(String id) {
        return alienDao.findById(Deployment.class, id);
    }

    /**
     * Get a deployment given its id
     *
     * @param id id of the deployment
     * @return deployment with given id
     */
    public Deployment getOrfail(String id) {
        Deployment deployment = alienDao.findById(Deployment.class, id);
        if (deployment == null) {
            throw new NotFoundException("Deployment <" + id + "> doesn't exist.");
        }
        return deployment;
    }

    /**
     * Get an active Deployment for a given environment or throw a NotFoundException if no active deployment can be found for this environment.
     *
     * @param environmentId Id of the application environment.
     * @return The active deployment for this environment
     */
    public Deployment getActiveDeploymentOrFail(String environmentId) {
        Deployment deployment = getActiveDeployment(environmentId);
        if (deployment == null) {
            throw new NotFoundException("Deployment for environment <" + environmentId + "> doesn't exist.");
        }
        return deployment;
    }

    /**
     * Get a deployment for a given environment/
     *
     * @param applicationEnvironmentId id of the environment
     * @return active deployment if exist or the last, null if the application environment has not been deployed
     */
    public Deployment getDeployment(String applicationEnvironmentId) {
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "environmentId" },
                new String[][] { new String[] { applicationEnvironmentId } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.search(Deployment.class, null, activeDeploymentFilters, null, null, 0, Integer.MAX_VALUE,
                "endDate", true);
        if (dataResult.getData() != null && dataResult.getData().length > 0) {
            if (dataResult.getData()[dataResult.getData().length - 1].getEndDate() == null) {
                return dataResult.getData()[dataResult.getData().length - 1];
            } else {
                return dataResult.getData()[0];
            }
        }
        return null;
    }

    /**
     * Get an active deployment for a given environment
     *
     * @param applicationEnvironmentId id of the environment
     * @return active deployment or null if not exist
     */
    public Deployment getActiveDeployment(String applicationEnvironmentId) {
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "environmentId", "endDate" },
                new String[][] { new String[] { applicationEnvironmentId }, new String[] { null } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.search(Deployment.class, null, activeDeploymentFilters, 1);
        if (dataResult.getData() != null && dataResult.getData().length > 0) {
            return dataResult.getData()[0];
        }
        return null;
    }

    /**
     * Get an active Deployment for a given cloud and topology or throw a NotFoundException if no active deployment can be found.
     *
     * @param topologyId id of the topology that has been deployed
     * @param orchestratorId id of the target orchestrator.
     * @return a deployment
     * @throws alien4cloud.exception.NotFoundException if not any deployment exists
     */
    public Deployment getActiveDeploymentOrFail(String topologyId, String orchestratorId) {
        Deployment deployment = getActiveDeployment(orchestratorId, topologyId);
        if (deployment == null) {
            throw new NotFoundException("Deployment for cloud <" + orchestratorId + "> and topology <" + topologyId + "> doesn't exist.");
        }
        return deployment;
    }

    /**
     * Get a topology for a given cloud / topology
     *
     * @param orchestratorId targeted orchestrator id
     * @param topologyId id of the topology to deploy
     * @return a deployment
     */
    public Deployment getActiveDeployment(String orchestratorId, String topologyId) {
        Deployment deployment = null;
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "orchestratorId", "topologyId", "endDate" },
                new String[][] { new String[] { orchestratorId }, new String[] { topologyId }, new String[] { null } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.search(Deployment.class, null, activeDeploymentFilters, 1);
        if (dataResult.getData() != null && dataResult.getData().length > 0) {
            deployment = dataResult.getData()[0];
        }
        return deployment;
    }

    /**
     * Check if there is an active deployment on a given orchestrator with the given orchestrator deployment id.
     *
     * @param orchestratorId The if of the orchestrator for which to check if there is a deployment with the given orchestratorDeploymentId.
     * @param orchestratorDeploymentId Unique if of the deployment for a given orchestrator
     * @return True if there is an active deployment for theses ids, false if not.
     */
    public boolean isActiveDeployment(String orchestratorId, String orchestratorDeploymentId) {
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "orchestratorId", "orchestratorDeploymentId", "endDate" },
                new String[][] { new String[] { orchestratorId }, new String[] { orchestratorDeploymentId }, new String[] { null } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.find(Deployment.class, activeDeploymentFilters, 1);
        if (dataResult.getData() != null && dataResult.getData().length > 0) {
            return true;
        }
        return false;
    }

    public Map<String, PaaSTopologyDeploymentContext> getCloudActiveDeploymentContexts(String orchestratorId) {
        Deployment[] deployments = getOrchestratorActiveDeployments(orchestratorId);
        Map<String, PaaSTopologyDeploymentContext> activeDeploymentContexts = Maps.newHashMap();
        for (Deployment deployment : deployments) {
            DeploymentTopology topology = deploymentRuntimeStateService.getRuntimeTopology(deployment.getId());
            activeDeploymentContexts.put(deployment.getOrchestratorDeploymentId(),
                    deploymentContextService.buildTopologyDeploymentContext(deployment, deploymentTopologyService.getLocations(topology), topology));
        }
        return activeDeploymentContexts;
    }

    private Deployment[] getOrchestratorActiveDeployments(String orchestratorId) {
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "orchestratorId", "endDate" },
                new String[][] { new String[] { orchestratorId }, new String[] { null } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.search(Deployment.class, null, activeDeploymentFilters, Integer.MAX_VALUE);
        return dataResult.getData();
    }

    public Map<String, Set<String>> getAllOrchestratorIdsAndOrchestratorDeploymentId(String applicationEnvironmentId) {
        Map<String, Set<String>> result = new HashMap<>();
        Map<String, String[]> activeDeploymentFilters = MapUtil.newHashMap(new String[] { "environmentId" },
                new String[][] { new String[] { applicationEnvironmentId } });
        GetMultipleDataResult<Deployment> dataResult = alienDao.search(Deployment.class, null, activeDeploymentFilters, Integer.MAX_VALUE);
        if (dataResult.getData() != null && dataResult.getData().length > 0) {
            for (Deployment deployment : dataResult.getData()) {
                if (!result.containsKey(deployment.getOrchestratorId())) {
                    result.put(deployment.getOrchestratorId(), new HashSet<String>());
                }
                result.get(deployment.getOrchestratorId()).add(deployment.getOrchestratorDeploymentId());
            }
        }
        return result;
    }

    /**
     * Switch a deployment to undeployed.
     *
     * @param deployment the deployment to switch.
     */
    public void markUndeployed(Deployment deployment) {
        if (deployment.getEndDate() == null) {
            deployment.setEndDate(new Date());
            alienDao.save(deployment);
            // Switch the deployed field of the Deployment topology to false
            DeploymentTopology deploymentTopology = alienMonitorDao.findById(DeploymentTopology.class, deployment.getId());
            deploymentTopology.setDeployed(false);
            alienMonitorDao.save(deploymentTopology);
        } else {
            log.info("Deployment <" + deployment.getId() + "> is already marked as undeployed.");
        }
    }

    /**
     * Check if a CSAR is currently deployed through dependencies in a topology.
     *
     * @return True if the archive is used in a deployment, false if not.
     */
    public boolean isArchiveDeployed(String archiveName, String archiveVersion) {
        FilterBuilder filter = FilterBuilders.boolFilter().must(FilterBuilders.termFilter("isDeployed", true))
                .must(FilterBuilders.nestedFilter("dependencies", FilterBuilders.boolFilter().must(FilterBuilders.termFilter("dependencies.name", archiveName))
                        .must(FilterBuilders.termFilter("dependencies.version", archiveVersion))));
        // Look if there is 1 matching element.
        GetMultipleDataResult<DeploymentTopology> result = alienMonitorDao.search(DeploymentTopology.class, null, null, filter, null, 0, 1);
        return result.getData() != null && result.getData().length > 0;
    }
}