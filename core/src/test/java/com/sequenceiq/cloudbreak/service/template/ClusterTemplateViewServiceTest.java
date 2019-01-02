package com.sequenceiq.cloudbreak.service.template;

import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;

import com.sequenceiq.cloudbreak.controller.exception.BadRequestException;
import com.sequenceiq.cloudbreak.domain.stack.cluster.ClusterTemplateView;

public class ClusterTemplateViewServiceTest {

    @InjectMocks
    private ClusterTemplateViewService underTest;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test(expected = BadRequestException.class)
    public void testPrepareCreation() {
        underTest.prepareCreation(new ClusterTemplateView());
    }

    @Test(expected = BadRequestException.class)
    public void testPrepareDeletion() {
        underTest.prepareDeletion(new ClusterTemplateView());
    }

}
