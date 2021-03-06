package com.sequenceiq.cloudbreak.api.model.environment.request;

import java.util.HashSet;
import java.util.Set;

import com.sequenceiq.cloudbreak.doc.ModelDescriptions.EnvironmentRequestModelDescription;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel
public abstract class EnvironmentBaseRequest {

    @ApiModelProperty(EnvironmentRequestModelDescription.PROXY_CONFIGS)
    private Set<String> proxyConfigs = new HashSet<>();

    @ApiModelProperty(EnvironmentRequestModelDescription.LDAP_CONFIGS)
    private Set<String> ldapConfigs = new HashSet<>();

    @ApiModelProperty(EnvironmentRequestModelDescription.RDS_CONFIGS)
    private Set<String> rdsConfigs = new HashSet<>();

    @ApiModelProperty(EnvironmentRequestModelDescription.KUBERNETES_CONFIGS)
    private Set<String> kubernetesConfigs = new HashSet<>();

    @ApiModelProperty(EnvironmentRequestModelDescription.KERBEROS_CONFIGS)
    private Set<String> kerberosConfigs = new HashSet<>();

    public Set<String> getProxyConfigs() {
        return proxyConfigs;
    }

    public void setProxyConfigs(Set<String> proxyConfigs) {
        this.proxyConfigs = proxyConfigs == null ? new HashSet<>() : proxyConfigs;
    }

    public Set<String> getLdapConfigs() {
        return ldapConfigs;
    }

    public void setLdapConfigs(Set<String> ldapConfigs) {
        this.ldapConfigs = ldapConfigs == null ? new HashSet<>() : ldapConfigs;
    }

    public Set<String> getRdsConfigs() {
        return rdsConfigs;
    }

    public void setRdsConfigs(Set<String> rdsConfigs) {
        this.rdsConfigs = rdsConfigs == null ? new HashSet<>() : rdsConfigs;
    }

    public Set<String> getKubernetesConfigs() {
        return kubernetesConfigs;
    }

    public void setKubernetesConfigs(Set<String> kubernetesConfigs) {
        this.kubernetesConfigs = kubernetesConfigs == null ? new HashSet<>() : kubernetesConfigs;
    }

    public Set<String> getKerberosConfigs() {
        return kerberosConfigs;
    }

    public void setKerberosConfigs(Set<String> kerberosConfigs) {
        this.kerberosConfigs = kerberosConfigs == null ? new HashSet<>() : kerberosConfigs;
    }
}
