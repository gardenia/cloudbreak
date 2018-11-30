package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleRequest;

public class AmbariClusterUpscaleStartResult extends AbstractClusterScaleRequest {
    public AmbariClusterUpscaleStartResult(Long stackId, String hostGroupName) {
        super(stackId, hostGroupName);
    }
}
