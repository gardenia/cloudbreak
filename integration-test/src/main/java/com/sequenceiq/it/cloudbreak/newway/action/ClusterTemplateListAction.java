package com.sequenceiq.it.cloudbreak.newway.action;

import static com.sequenceiq.it.cloudbreak.newway.log.Log.logJSON;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.cloudbreak.api.model.template.ClusterTemplateViewResponse;
import com.sequenceiq.it.cloudbreak.newway.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.newway.context.TestContext;
import com.sequenceiq.it.cloudbreak.newway.entity.ClusterTemplateViewEntity;

public class ClusterTemplateListAction implements ActionV2<ClusterTemplateViewEntity> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTemplateListAction.class);

    @Override
    public ClusterTemplateViewEntity action(TestContext testContext, ClusterTemplateViewEntity entity, CloudbreakClient client) throws Exception {
        logJSON(LOGGER, " ClusterTemplateViewEntity get request:\n", entity.getRequest());
        Set<ClusterTemplateViewResponse> responses = client.getCloudbreakClient()
                .clusterTemplateV3EndPoint()
                .listByWorkspace(client.getWorkspaceId());
        entity.setResponses(responses);
        logJSON(LOGGER, " ClusterTemplateEntity list successfully:\n", responses);
        return entity;
    }
}
