package org.alien4cloud.tosca.editor.services;

import java.nio.file.Path;

import javax.annotation.Resource;
import javax.inject.Inject;

import org.alien4cloud.tosca.catalog.ArchiveParserUtil;
import org.alien4cloud.tosca.catalog.IArchivePostProcessor;
import org.alien4cloud.tosca.editor.EditionContextManager;
import org.alien4cloud.tosca.editor.exception.EditorToscaYamlInvalidException;
import org.alien4cloud.tosca.editor.exception.EditorToscaYamlNotSupportedException;
import org.alien4cloud.tosca.editor.exception.EditorToscaYamlParsingException;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractToscaType;
import org.springframework.stereotype.Component;

import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaArchiveParser;

/**
 * Process the upload of a topology in the context of the editor.
 */
@Component
public class EditorTopologyUploadService {
    @Inject
    private ToscaArchiveParser toscaArchiveParser;
    @Resource(name = "editorArchivePostProcessor")
    private IArchivePostProcessor postProcessor;
    @Inject
    private WorkflowsBuilderService workflowBuilderService;

    public void processTopologyDir(Path archivePath, String workspace) {
        // parse the archive.
        try {
            ParsingResult<ArchiveRoot> parsingResult = toscaArchiveParser.parseDir(archivePath);
            processTopologyParseResult(archivePath, parsingResult, workspace);
        } catch (ParsingException e) {
            // Manage parsing error and dispatch them in the right editor exception
            throw new EditorToscaYamlInvalidException("The uploaded file to override the topology yaml is not a valid Tosca Yaml.");
        }
    }

    /**
     * Process the import of a topology archive or yaml in the context of the editor.
     *
     * @param archivePath The path of the yaml or archive.
     */
    public void processTopology(Path archivePath, String workspace) {
        // parse the archive.
        try {
            ParsingResult<ArchiveRoot> parsingResult = toscaArchiveParser.parse(archivePath, true);
            processTopologyParseResult(archivePath, parsingResult, workspace);
        } catch (ParsingException e) {
            // Manage parsing error and dispatch them in the right editor exception
            throw new EditorToscaYamlParsingException("The uploaded file to override the topology yaml is not a valid Tosca Yaml.");
        }
    }

    private void processTopologyParseResult(Path archivePath, ParsingResult<ArchiveRoot> parsingResult, String workspace) {
        // parse the archive.
        parsingResult = postProcessor.process(archivePath, parsingResult, workspace);
        // check if any blocker error has been found during parsing process.
        if (parsingResult.hasError(ParsingErrorLevel.ERROR)) {
            // do not save anything if any blocker error has been found during import.
            throw new EditorToscaYamlParsingException("Uploaded yaml files is not a valid tosca template", ArchiveParserUtil.toSimpleResult(parsingResult));
        }
        if (parsingResult.getResult().hasToscaTypes()) {
            throw new EditorToscaYamlNotSupportedException("Tosca types are currently not supported in the topology editor context.");
        }
        if (!parsingResult.getResult().hasToscaTopologyTemplate()) {
            throw new EditorToscaYamlNotSupportedException("A topology template is required in the topology edition context.");
        }

        Topology currentTopology = EditionContextManager.getTopology();
        Topology parsedTopology = parsingResult.getResult().getTopology();

        if (!currentTopology.getArchiveName().equals(parsedTopology.getArchiveName())
                || !currentTopology.getArchiveVersion().equals(parsedTopology.getArchiveVersion())) {
            throw new EditorToscaYamlNotSupportedException(
                    "Template name and version must be set to [" + currentTopology.getArchiveName() + ":" + currentTopology.getArchiveVersion()
                            + "] and cannot be updated to [" + parsedTopology.getArchiveName() + ":" + parsedTopology.getArchiveVersion() + "]");
        }

        // Copy static elements from the topology
        parsedTopology.setId(currentTopology.getId());
        // Update editor tosca context
        ToscaContext.get().resetDependencies(parsedTopology.getDependencies());

        // init the workflows for the topology based on the yaml
        WorkflowsBuilderService.TopologyContext topologyContext = workflowBuilderService
                .buildCachedTopologyContext(new WorkflowsBuilderService.TopologyContext() {
                    @Override
                    public Topology getTopology() {
                        return parsedTopology;
                    }

                    @Override
                    public <T extends AbstractToscaType> T findElement(Class<T> clazz, String id) {
                        return ToscaContext.get(clazz, id);
                    }
                });
        workflowBuilderService.initWorkflows(topologyContext);

        // update the topology in the edition context with the new one
        EditionContextManager.get().setTopology(parsingResult.getResult().getTopology());
    }
}