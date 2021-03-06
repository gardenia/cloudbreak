package com.sequenceiq.cloudbreak.controller;

import static com.sequenceiq.cloudbreak.cloud.model.Platform.platform;
import static com.sequenceiq.cloudbreak.util.SqlUtil.getProperSqlErrorMessage;

import java.io.IOException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.model.stack.StackRequest;
import com.sequenceiq.cloudbreak.api.model.stack.StackResponse;
import com.sequenceiq.cloudbreak.api.model.stack.StackValidationRequest;
import com.sequenceiq.cloudbreak.api.model.stack.cluster.ClusterRequest;
import com.sequenceiq.cloudbreak.api.model.stack.instance.InstanceStatus;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.common.model.user.CloudbreakUser;
import com.sequenceiq.cloudbreak.common.type.APIResourceType;
import com.sequenceiq.cloudbreak.controller.exception.BadRequestException;
import com.sequenceiq.cloudbreak.controller.validation.ParametersValidator;
import com.sequenceiq.cloudbreak.controller.validation.ValidationResult;
import com.sequenceiq.cloudbreak.controller.validation.ValidationResult.State;
import com.sequenceiq.cloudbreak.controller.validation.Validator;
import com.sequenceiq.cloudbreak.controller.validation.filesystem.FileSystemValidator;
import com.sequenceiq.cloudbreak.controller.validation.template.TemplateValidator;
import com.sequenceiq.cloudbreak.converter.spi.CredentialToCloudCredentialConverter;
import com.sequenceiq.cloudbreak.core.CloudbreakImageCatalogException;
import com.sequenceiq.cloudbreak.core.CloudbreakImageNotFoundException;
import com.sequenceiq.cloudbreak.core.flow2.service.ReactorFlowManager;
import com.sequenceiq.cloudbreak.domain.Blueprint;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.StackType;
import com.sequenceiq.cloudbreak.domain.stack.StackValidation;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.DatalakeResources;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceGroup;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.workspace.User;
import com.sequenceiq.cloudbreak.domain.workspace.Workspace;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.service.ClusterCreationSetupService;
import com.sequenceiq.cloudbreak.service.StackUnderOperationService;
import com.sequenceiq.cloudbreak.service.TransactionService;
import com.sequenceiq.cloudbreak.service.TransactionService.TransactionExecutionException;
import com.sequenceiq.cloudbreak.service.TransactionService.TransactionRuntimeExecutionException;
import com.sequenceiq.cloudbreak.service.blueprint.BlueprintService;
import com.sequenceiq.cloudbreak.service.credential.CredentialPrerequisiteService;
import com.sequenceiq.cloudbreak.service.datalake.DatalakeResourcesService;
import com.sequenceiq.cloudbreak.service.decorator.StackDecorator;
import com.sequenceiq.cloudbreak.service.image.ImageService;
import com.sequenceiq.cloudbreak.service.image.StatedImage;
import com.sequenceiq.cloudbreak.service.sharedservice.SharedServiceConfigProvider;
import com.sequenceiq.cloudbreak.service.stack.StackService;

@Service
public class StackCreatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackCreatorService.class);

    @Inject
    private StackDecorator stackDecorator;

    @Inject
    private FileSystemValidator fileSystemValidator;

    @Inject
    private CredentialToCloudCredentialConverter credentialToCloudCredentialConverter;

    @Inject
    private ClusterCreationSetupService clusterCreationService;

    @Inject
    private StackService stackService;

    @Inject
    private ReactorFlowManager flowManager;

    @Inject
    private ImageService imageService;

    @Inject
    @Qualifier("conversionService")
    private ConversionService conversionService;

    @Inject
    private TemplateValidator templateValidator;

    @Inject
    private SharedServiceConfigProvider sharedServiceConfigProvider;

    @Inject
    private Validator<StackRequest> stackRequestValidator;

    @Inject
    private TransactionService transactionService;

    @Inject
    private StackUnderOperationService stackUnderOperationService;

    @Inject
    private ParametersValidator parametersValidator;

    @Inject
    private BlueprintService blueprintService;

    @Inject
    private CredentialPrerequisiteService credentialPrerequisiteService;

    @Inject
    private DatalakeResourcesService datalakeResourcesService;

    public StackResponse createStack(CloudbreakUser cloudbreakUser, User user, Workspace workspace, StackRequest stackRequest) {
        ValidationResult validationResult = stackRequestValidator.validate(stackRequest);
        if (validationResult.getState() == State.ERROR) {
            LOGGER.debug("Stack request has validation error(s): {}.", validationResult.getFormattedErrors());
            throw new BadRequestException(validationResult.getFormattedErrors());
        }

        Stack savedStack;
        try {
            savedStack = transactionService.required(() -> {
                long start = System.currentTimeMillis();
                ensureStackDoesNotExists(stackRequest.getName(), workspace);

                Stack stack = conversionService.convert(stackRequest, Stack.class);

                stack.setWorkspace(workspace);
                stack.setCreator(user);

                String stackName = stack.getName();
                LOGGER.debug("Stack request converted to stack in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                MDCBuilder.buildMdcContext(stack);

                start = System.currentTimeMillis();
                LOGGER.debug("Stack propagated with sensitive data in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                start = System.currentTimeMillis();
                stack = stackDecorator.decorate(stack, stackRequest, user, workspace);
                LOGGER.debug("Stack object has been decorated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                if (stack.getOrchestrator() != null && stack.getOrchestrator().getApiEndpoint() != null) {
                    stackService.validateOrchestrator(stack.getOrchestrator());
                }

                start = System.currentTimeMillis();
                for (InstanceGroup instanceGroup : stack.getInstanceGroups()) {
                    templateValidator.validateTemplateRequest(stack.getCredential(), instanceGroup.getTemplate(), stack.getRegion(),
                            stack.getAvailabilityZone(), stack.getPlatformVariant());
                }
                LOGGER.debug("Stack's instance templates have been validated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                CloudCredential cloudCredential = credentialToCloudCredentialConverter.convert(stack.getCredential());
                Blueprint blueprint = null;
                if (stackRequest.getClusterRequest() != null) {
                    start = System.currentTimeMillis();
                    StackValidationRequest stackValidationRequest = conversionService.convert(stackRequest, StackValidationRequest.class);
                    LOGGER.debug("Stack validation request has been created in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                    start = System.currentTimeMillis();
                    StackValidation stackValidation = conversionService.convert(stackValidationRequest, StackValidation.class);
                    LOGGER.debug("Stack validation object has been created in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                    blueprint = stackValidation.getBlueprint();
                    setStackTypeAndValidateDatalake(stack, blueprint);

                    start = System.currentTimeMillis();
                    stackService.validateStack(stackValidation, stackRequest.getClusterRequest().getValidateBlueprint());
                    LOGGER.debug("Stack has been validated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                    start = System.currentTimeMillis();
                    fileSystemValidator.validateFileSystem(stackValidationRequest.getPlatform(), cloudCredential, stackValidationRequest.getFileSystem(),
                            stack.getCreator().getUserId(), stack.getWorkspace().getId());
                    LOGGER.debug("Filesystem has been validated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                    start = System.currentTimeMillis();
                    clusterCreationService.validate(stackRequest.getClusterRequest(), cloudCredential, stack, user, workspace);
                    LOGGER.debug("Cluster has been validated in {} ms for stack {}", System.currentTimeMillis() - start, stackName);
                }

                start = System.currentTimeMillis();
                parametersValidator.validate(stackRequest.getCloudPlatform(), cloudCredential, stack.getParameters(), stack.getCreator().getUserId(),
                        stack.getWorkspace().getId());
                LOGGER.debug("Parameter validation has been finished under {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                start = System.currentTimeMillis();
                String platformString = platform(stack.cloudPlatform()).value().toLowerCase();
                StatedImage imgFromCatalog;
                try {
                    imgFromCatalog = imageService.determineImageFromCatalog(workspace.getId(),
                            stackRequest.getImageId(), platformString, stackRequest.getImageCatalog(), blueprint,
                            shouldUseBaseImage(stackRequest.getClusterRequest()), stackRequest.getOs(), cloudbreakUser, user);
                } catch (CloudbreakImageNotFoundException | CloudbreakImageCatalogException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                LOGGER.debug("Image for stack has been determined under {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                fillInstanceMetadata(stack);

                start = System.currentTimeMillis();
                stack = stackService.create(stack, platformString, imgFromCatalog, user, workspace);
                LOGGER.debug("Stack object and its dependencies has been created in {} ms for stack {}", System.currentTimeMillis() - start, stackName);

                decorateWithDatalakeResourceId(stack);
                try {
                    createClusterIfNeed(user, workspace, stackRequest, stack, stackName, blueprint);
                } catch (CloudbreakImageNotFoundException | IOException | TransactionExecutionException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                prepareSharedServiceIfNeed(stackRequest, stack);
                return stack;
            });
        } catch (TransactionExecutionException e) {
            stackUnderOperationService.off();
            if (e.getCause() instanceof DataIntegrityViolationException) {
                String msg = String.format("Error with resource [%s], error: [%s]", APIResourceType.STACK,
                        getProperSqlErrorMessage((DataIntegrityViolationException) e.getCause()));
                throw new BadRequestException(msg);
            }
            throw new TransactionRuntimeExecutionException(e);
        }

        long start = System.currentTimeMillis();
        StackResponse response = conversionService.convert(savedStack, StackResponse.class);
        LOGGER.debug("Stack response has been created in {} ms for stack {}", System.currentTimeMillis() - start, savedStack.getName());

        start = System.currentTimeMillis();
        flowManager.triggerProvisioning(savedStack.getId());
        LOGGER.debug("Stack provision triggered in {} ms for stack {}", System.currentTimeMillis() - start, savedStack.getName());

        return response;
    }

    private void decorateWithDatalakeResourceId(Stack stack) {
        if (stack.getEnvironment() != null
                && stack.getEnvironment().getDatalakeResourcesId() != null && stack.getDatalakeResourceId() == null) {
            stack.setDatalakeResourceId(stack.getEnvironment().getDatalakeResourcesId());
        } else if (stack.getDatalakeId() != null) {
            DatalakeResources datalakeResources = datalakeResourcesService.getDatalakeResources(stack.getDatalakeId());
            if (datalakeResources != null) {
                stack.setDatalakeResourceId(datalakeResources.getId());
            }
        }
    }

    private void setStackTypeAndValidateDatalake(Stack stack, Blueprint blueprint) {
        if (blueprintService.isDatalakeBlueprint(blueprint)) {
            stack.setType(StackType.DATALAKE);
            if (stack.getEnvironment() != null) {
                Long datalakesInEnv = stackService.countDatalakeStacksInEnvironment(stack.getEnvironment().getId());
                if (datalakesInEnv >= 1L) {
                    throw new BadRequestException("Only 1 datalake cluster / environment is allowed.");
                }
            }
        } else if (stack.getType() == null) {
            stack.setType(StackType.WORKLOAD);
        }
    }

    private void fillInstanceMetadata(Stack stack) {
        long privateIdNumber = 0;
        for (InstanceGroup instanceGroup : stack.getInstanceGroups()) {
            for (InstanceMetaData instanceMetaData : instanceGroup.getAllInstanceMetaData()) {
                instanceMetaData.setPrivateId(privateIdNumber);
                instanceMetaData.setInstanceStatus(InstanceStatus.REQUESTED);
                privateIdNumber++;
            }
        }
    }

    private boolean shouldUseBaseImage(ClusterRequest clusterRequest) {
        return clusterRequest.getAmbariRepoDetailsJson() != null || clusterRequest.getAmbariStackDetails() != null;
    }

    private Stack prepareSharedServiceIfNeed(StackRequest stackRequest, Stack stack) {
        if (credentialPrerequisiteService.isCumulusCredential(stack.getCredential().getAttributes())
                || stack.getDatalakeResourceId() != null
                || (stackRequest.getClusterRequest() != null && stackRequest.getClusterRequest().getConnectedCluster() != null)) {
            long start = System.currentTimeMillis();
            stack = sharedServiceConfigProvider.prepareDatalakeConfigs(stack);
            LOGGER.debug("Cluster object and its dependencies has been created in {} ms for stack {}", System.currentTimeMillis() - start, stack.getName());
        }
        return stack;
    }

    private void createClusterIfNeed(User user, Workspace workspace, StackRequest stackRequest, Stack stack, String stackName, Blueprint blueprint)
            throws CloudbreakImageNotFoundException, IOException, TransactionExecutionException {
        if (stackRequest.getClusterRequest() != null) {
            long start = System.currentTimeMillis();
            Cluster cluster = clusterCreationService.prepare(stackRequest.getClusterRequest(), stack, blueprint, user, workspace);
            LOGGER.debug("Cluster object and its dependencies has been created in {} ms for stack {}", System.currentTimeMillis() - start, stackName);
            stack.setCluster(cluster);
        }
    }

    private void ensureStackDoesNotExists(String stackName, Workspace workspace) {
        Stack stack = stackService.findStackByNameAndWorkspaceId(stackName, workspace.getId());
        if (stack != null) {
            throw new BadRequestException("Cluster already exists: " + stackName);
        }
    }
}
