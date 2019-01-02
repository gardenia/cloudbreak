package com.sequenceiq.it.cloudbreak.newway;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.sequenceiq.it.IntegrationTestContext;
import com.sequenceiq.it.cloudbreak.newway.entity.ClusterTemplateViewEntity;

public class ClusterTemplateView extends ClusterTemplateViewEntity {

    static Function<IntegrationTestContext, ClusterTemplateView> getTestContext(String key) {
        return testContext -> testContext.getContextParam(key, ClusterTemplateView.class);
    }

    static Function<IntegrationTestContext, ClusterTemplateView> getNew() {
        return testContext -> new ClusterTemplateView();
    }

    public static Action<ClusterTemplateView> getAll() {
        return new Action<>(getNew(), ClusterTemplateAction::getAll);
    }

    public static Assertion<ClusterTemplateView> assertThis(BiConsumer<ClusterTemplateView, IntegrationTestContext> check) {
        return new Assertion<>(getTestContext(GherkinTest.RESULT), check);
    }

    @Override
    public ClusterTemplateView withName(String name) {
        return (ClusterTemplateView) super.withName(name);
    }

    @Override
    public ClusterTemplateView withDescription(String description) {
        return (ClusterTemplateView) super.withDescription(description);
    }
}
