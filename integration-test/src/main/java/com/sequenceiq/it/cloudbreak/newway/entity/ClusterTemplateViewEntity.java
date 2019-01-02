package com.sequenceiq.it.cloudbreak.newway.entity;

import java.util.Collection;

import com.sequenceiq.cloudbreak.api.model.template.ClusterTemplateRequest;
import com.sequenceiq.cloudbreak.api.model.template.ClusterTemplateType;
import com.sequenceiq.cloudbreak.api.model.template.ClusterTemplateViewResponse;
import com.sequenceiq.it.cloudbreak.newway.AbstractCloudbreakEntity;
import com.sequenceiq.it.cloudbreak.newway.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.newway.Prototype;
import com.sequenceiq.it.cloudbreak.newway.context.Purgable;
import com.sequenceiq.it.cloudbreak.newway.context.TestContext;

@Prototype
public class ClusterTemplateViewEntity extends AbstractCloudbreakEntity<ClusterTemplateRequest, ClusterTemplateViewResponse, ClusterTemplateViewEntity>
        implements Purgable<ClusterTemplateViewResponse> {

    public ClusterTemplateViewEntity(TestContext testContext) {
        super(new ClusterTemplateRequest(), testContext);
    }

    public ClusterTemplateViewEntity() {
        super(ClusterTemplateViewEntity.class.getSimpleName().toUpperCase());
    }

    public ClusterTemplateViewEntity valid() {
        return withName(getNameCreator().getRandomNameForMock())
                .withStackTemplate(getTestContext().init(StackTemplateEntity.class));
    }

    public ClusterTemplateViewEntity withName(String name) {
        getRequest().setName(name);
        setName(name);
        return this;
    }

    public ClusterTemplateViewEntity withoutStackTemplate() {
        getRequest().setStackTemplate(null);
        return this;
    }

    public ClusterTemplateViewEntity withStackTemplate(StackTemplateEntity stackTemplate) {
        getRequest().setStackTemplate(stackTemplate.getRequest());
        return this;
    }

    public ClusterTemplateViewEntity withStackTemplate(String key) {
        StackTemplateEntity stackTemplate = getTestContext().get(key);
        getRequest().setStackTemplate(stackTemplate.getRequest());
        return this;
    }

    public ClusterTemplateViewEntity withDescription(String description) {
        getRequest().setDescription(description);
        return this;
    }

    public ClusterTemplateViewEntity withType(ClusterTemplateType type) {
        getRequest().setType(type);
        return this;
    }

    @Override
    public Collection<ClusterTemplateViewResponse> getAll(CloudbreakClient client) {
        return client.getCloudbreakClient().clusterTemplateV3EndPoint().listByWorkspace(client.getWorkspaceId());
    }

    @Override
    public boolean deletable(ClusterTemplateViewResponse entity) {
        return entity.getName().startsWith("mock-");
    }

    @Override
    public void delete(ClusterTemplateViewResponse entity, CloudbreakClient client) {
        client.getCloudbreakClient().clusterTemplateV3EndPoint().deleteInWorkspace(client.getWorkspaceId(), entity.getName());
    }

    @Override
    public void cleanUp(TestContext context, CloudbreakClient cloudbreakClient) {
        delete(getResponse(), cloudbreakClient);
    }

    public Long count() {
        CloudbreakClient client = getTestContext().getCloudbreakClient();
        return (long) client.getCloudbreakClient()
                .clusterTemplateV3EndPoint()
                .listByWorkspace(client.getWorkspaceId()).size();
    }
}
