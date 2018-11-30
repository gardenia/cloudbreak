package com.sequenceiq.cloudbreak.core.flow2.event;

import java.util.Collections;
import java.util.Set;

import com.sequenceiq.cloudbreak.common.type.ScalingType;

public class StackAndClusterUpscaleTriggerEvent extends StackScaleTriggerEvent {

    private final ScalingType scalingType;

    private final boolean singleMasterGateway;

    public StackAndClusterUpscaleTriggerEvent(String selector, Long stackId, String instanceGroup, Integer adjustment, ScalingType scalingType) {
        this(selector, stackId, instanceGroup, adjustment, scalingType, Collections.emptySet(), false);
    }

    public StackAndClusterUpscaleTriggerEvent(String selector, Long stackId,
            String instanceGroup, Integer adjustment, ScalingType scalingType, Set<String> hostNames, boolean singlePrimaryGateway) {
        super(selector, stackId, instanceGroup, adjustment, hostNames);
        this.scalingType = scalingType;
        this.singleMasterGateway = singlePrimaryGateway;
    }

    public ScalingType getScalingType() {
        return scalingType;
    }

    public boolean isSingleMasterGateway() {
        return singleMasterGateway;
    }
}
