package com.sequenceiq.it.spark.salt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmMetaDataStatus;
import com.sequenceiq.cloudbreak.cloud.model.InstanceStatus;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.ApplyResponse;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.MinionIpAddressesResponse;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.MinionStatus;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.MinionStatusSaltResponse;
import com.sequenceiq.cloudbreak.util.JsonUtil;
import com.sequenceiq.it.spark.ITResponse;

import spark.Request;
import spark.Response;

public class SaltApiRunPostResponse extends ITResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaltApiRunPostResponse.class);

    private final Map<String, CloudVmMetaDataStatus> instanceMap;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SaltApiRunPostResponse(Map<String, CloudVmMetaDataStatus> instanceMap) {
        this.instanceMap = instanceMap;
        objectMapper.setVisibility(objectMapper.getVisibilityChecker().withGetterVisibility(Visibility.NONE));
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String body = request.body();
        return createSaltApiResponse(body);
    }

    public Object createSaltApiResponse(String body) throws JsonProcessingException {
        if (body.contains("manage.status")) {
            return minionStatuses();
        }
        if (body.contains("network.ipaddrs")) {
            return ipAddresses();
        }
        if (body.contains("grains.append")) {
            return grainsResponse();
        }
        if (body.contains("grains.remove")) {
            return grainsResponse();
        }
        if (body.contains("saltutil.sync_all")) {
            return saltUtilSyncGrainsResponse();
        }
        if (body.contains("mine.update")) {
            return saltUtilSyncGrainsResponse();
        }
        if (body.contains("state.highstate")) {
            return stateHighState();
        }
        if (body.contains("jobs.active")) {
            return jobsActive();
        }
        if (body.contains("jobs.lookup_jid")) {
            return jobsLookupJid();
        }
        if (body.contains("state.apply")) {
            return stateApply();
        }
        if (body.contains("key.delete")) {
            return "";
        }
        if (body.contains("cmd.run")) {
            Pattern pattern = Pattern.compile("&tgt=([\\w\\.-]+)&");
            Matcher matcher = pattern.matcher(body);
            String host = matcher.find() ? matcher.group(1) : "";

            if (body.contains("arg=%28cd+%2Fsrv%2Fsalt%2Fdisk%3BCLOUD_PLATFORM%3D%27MOCK%27+ATTACHED_VOLUME_NAME_LIST%3D%27%27+"
                    + "ATTACHED_VOLUME_SERIAL_LIST%3D%27%27+.%2Ffind-device-and-format.sh%29")) {
                return String.format(responseFromJsonFile("saltapi/cmd_run_format_response.json"), host);
            } else if (body.contains("arg=cat+%2Fetc%2Ffstab")) {
                return String.format(responseFromJsonFile("saltapi/cmd_run_mount_response.json"), host);
            } else {
                return responseFromJsonFile("saltapi/cmd_run_empty_response.json");
            }
        }
        LOGGER.error("no response for this SALT RUN request: " + body);
        throw new IllegalStateException("no response for this SALT RUN request: " + body);
    }

    protected Object stateApply() {
        return responseFromJsonFile("saltapi/state_apply_response.json");
    }

    protected Object jobsLookupJid() {
        return responseFromJsonFile("saltapi/lookup_jid_response.json");
    }

    protected Object jobsActive() {
        return responseFromJsonFile("saltapi/runningjobs_response.json");
    }

    protected Object stateHighState() {
        return responseFromJsonFile("saltapi/high_state_response.json");
    }

    protected Object minionStatuses() throws JsonProcessingException {
        MinionStatusSaltResponse minionStatusSaltResponse = new MinionStatusSaltResponse();
        List<MinionStatus> minionStatusList = new ArrayList<>();
        MinionStatus minionStatus = new MinionStatus();
        ArrayList<String> upList = new ArrayList<>();
        minionStatus.setUp(upList);
        ArrayList<String> downList = new ArrayList<>();
        minionStatus.setDown(downList);
        minionStatusList.add(minionStatus);
        minionStatusSaltResponse.setResult(minionStatusList);

        for (Map.Entry<String, CloudVmMetaDataStatus> stringCloudVmMetaDataStatusEntry : instanceMap.entrySet()) {
            CloudVmMetaDataStatus cloudVmMetaDataStatus = stringCloudVmMetaDataStatusEntry.getValue();
            if (InstanceStatus.STARTED == cloudVmMetaDataStatus.getCloudVmInstanceStatus().getStatus()) {
                String privateIp = cloudVmMetaDataStatus.getMetaData().getPrivateIp();
                upList.add("host-" + privateIp.replace(".", "-"));
            }
        }
        // intentional new ObjectMapper() -> .withGetterVisibility(Visibility.NONE) screws up serializing
        return new ObjectMapper().writeValueAsString(minionStatusSaltResponse);
    }

    protected Object ipAddresses() throws JsonProcessingException {
        MinionIpAddressesResponse minionIpAddressesResponse = new MinionIpAddressesResponse();
        List<Map<String, JsonNode>> result = new ArrayList<>();

        for (Entry<String, CloudVmMetaDataStatus> stringCloudVmMetaDataStatusEntry : instanceMap.entrySet()) {
            CloudVmMetaDataStatus cloudVmMetaDataStatus = stringCloudVmMetaDataStatusEntry.getValue();
            if (InstanceStatus.STARTED == cloudVmMetaDataStatus.getCloudVmInstanceStatus().getStatus()) {
                String privateIp = cloudVmMetaDataStatus.getMetaData().getPrivateIp();
                Map<String, JsonNode> networkHashMap = new HashMap<>();
                try {
                    networkHashMap.put("host-" + privateIp.replace(".", "-"), JsonUtil.readTree("[\"" + privateIp + "\"]"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                result.add(networkHashMap);
            }
        }

        minionIpAddressesResponse.setResult(result);
        return objectMapper.writeValueAsString(minionIpAddressesResponse);
    }

    protected Object saltUtilSyncGrainsResponse() throws JsonProcessingException {
        ApplyResponse applyResponse = new ApplyResponse();
        List<Map<String, Object>> responseList = new ArrayList<>();

        Map<String, Object> hostMap = new HashMap<>();

        for (Entry<String, CloudVmMetaDataStatus> stringCloudVmMetaDataStatusEntry : instanceMap.entrySet()) {
            CloudVmMetaDataStatus cloudVmMetaDataStatus = stringCloudVmMetaDataStatusEntry.getValue();
            if (InstanceStatus.STARTED == cloudVmMetaDataStatus.getCloudVmInstanceStatus().getStatus()) {
                String privateIp = cloudVmMetaDataStatus.getMetaData().getPrivateIp();
                hostMap.put("host-" + privateIp.replace(".", "-"), privateIp);
            }
        }

        responseList.add(hostMap);

        applyResponse.setResult(responseList);
        return objectMapper.writeValueAsString(applyResponse);
    }

    protected Object grainsResponse() throws JsonProcessingException {
        ApplyResponse applyResponse = new ApplyResponse();
        List<Map<String, Object>> responseList = new ArrayList<>();

        Map<String, Object> hostMap = new HashMap<>();

        for (Entry<String, CloudVmMetaDataStatus> stringCloudVmMetaDataStatusEntry : instanceMap.entrySet()) {
            CloudVmMetaDataStatus cloudVmMetaDataStatus = stringCloudVmMetaDataStatusEntry.getValue();
            if (InstanceStatus.STARTED == cloudVmMetaDataStatus.getCloudVmInstanceStatus().getStatus()) {
                String privateIp = cloudVmMetaDataStatus.getMetaData().getPrivateIp();
                hostMap.put("host-" + privateIp.replace(".", "-"), privateIp);
            }
        }
        responseList.add(hostMap);

        applyResponse.setResult(responseList);
        return objectMapper.writeValueAsString(applyResponse);
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
