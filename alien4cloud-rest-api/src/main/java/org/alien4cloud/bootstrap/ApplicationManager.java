package org.alien4cloud.bootstrap;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import alien4cloud.FullApplicationConfiguration;
import alien4cloud.audit.rest.AuditController;
import alien4cloud.common.AlienConstants;
import alien4cloud.events.AlienEvent;
import alien4cloud.events.HALeaderElectionEvent;
import alien4cloud.webconfiguration.RestDocumentationHandlerProvider;
import alien4cloud.webconfiguration.RestDocumentationPluginsBootstrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Manage the child context defined in {@link FullApplicationConfiguration}: this context is launched when the current alien instance is elected as leader and
 * eventually destroyed in case of banish.
 */
@Component
@Slf4j
public class ApplicationManager implements ApplicationListener<AlienEvent>, HandlerMapping, PriorityOrdered {
    @Resource
    private ApplicationContext bootstrapContext;

    private AnnotationConfigApplicationContext fullApplicationContext;

    private RequestMappingHandlerMapping mapper;

    private volatile boolean childContextLaunched;

    @Value("#{environment.acceptsProfiles('" + AlienConstants.NO_API_DOC_PROFILE + "')}")
    private boolean apiDocDisabled;

    /**
     * A synchronized method is enough since the boolean <code>childContextLaunched</code> is only read/write in it.
     */
    @Override
    public synchronized void onApplicationEvent(AlienEvent event) {
        if (event instanceof HALeaderElectionEvent) {
            handleHALeaderElectionEvent((HALeaderElectionEvent) event);
        } else {
            handleAlienEvent(event);
        }
    }

    /**
     * receive an event, and forward it to child contexts.
     * Mark it as forwarded before, so that if wont be re-processed here, as an event published into a context is automatically published into the parent
     * context.
     * 
     * @param event
     */
    private void handleAlienEvent(AlienEvent event) {
        if (childContextLaunched) {
            if (!event.isForwarded()) {
                event.setForwarded(true);
                fullApplicationContext.publishEvent(event);
            } else {
                log.debug("Event {} already forwarded to children", event);
            }
        } else {
            log.debug("This instance is a backup. It doesn't have to process events.");
        }
    }

    /**
     * configure this host as leader / backup
     * 
     * @param event
     */
    private void handleHALeaderElectionEvent(HALeaderElectionEvent event) {
        if (event.isLeader()) {
            log.info("Leader Election event received, this instance is now known as the leader");
            if (!childContextLaunched) {
                log.info("Launching the full application context");

                fullApplicationContext = new AnnotationConfigApplicationContext();
                fullApplicationContext.setParent(this.bootstrapContext);
                fullApplicationContext.setId(this.bootstrapContext.getId() + ":full");
                fullApplicationContext.register(FullApplicationConfiguration.class);
                fullApplicationContext.refresh();
                fullApplicationContext.start();

                mapper = new RequestMappingHandlerMapping();
                mapper.setApplicationContext(fullApplicationContext);
                mapper.afterPropertiesSet();

                refreshDocumentation();

                AuditController auditController = fullApplicationContext.getBean(AuditController.class);
                auditController.register(mapper);

                childContextLaunched = true;
            } else {
                log.warn("The full application context is already launched, something seems wrong in the current state !");
            }
        } else {
            if (childContextLaunched) {
                log.info("Destroying the full application context");

                mapper = null;
                fullApplicationContext.destroy();

                childContextLaunched = false;
            } else {
                log.warn("The full application context is already destroyed, something seems wrong in the current state !");
            }
        }
    }

    private void refreshDocumentation() {
        if (!apiDocDisabled) {
            RestDocumentationHandlerProvider restDocumentationHandlerProvider = fullApplicationContext.getBean(RestDocumentationHandlerProvider.class);
            restDocumentationHandlerProvider.register(mapper);
            RestDocumentationPluginsBootstrapper documentationPluginsBootstrapper = fullApplicationContext.getBean(RestDocumentationPluginsBootstrapper.class);
            documentationPluginsBootstrapper.refresh();
        }
    }

    @Override
    public HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        if (mapper != null) {
            return mapper.getHandler(request);
        }
        return null;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

}
