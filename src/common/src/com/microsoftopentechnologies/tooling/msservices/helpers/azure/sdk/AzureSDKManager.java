/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoftopentechnologies.tooling.msservices.helpers.azure.sdk;

import com.microsoftopentechnologies.tooling.msservices.helpers.NotNull;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import com.microsoftopentechnologies.tooling.msservices.model.storage.StorageAccount;
import com.microsoftopentechnologies.tooling.msservices.model.vm.*;

import java.util.List;

public interface AzureSDKManager {
    @NotNull
    List<CloudService> getCloudServices(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<VirtualMachine> getVirtualMachines(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    VirtualMachine refreshVirtualMachineInformation(@NotNull VirtualMachine vm) throws AzureCmdException;

    void startVirtualMachine(@NotNull VirtualMachine vm) throws AzureCmdException;

    void shutdownVirtualMachine(@NotNull VirtualMachine vm, boolean deallocate) throws AzureCmdException;

    void restartVirtualMachine(@NotNull VirtualMachine vm) throws AzureCmdException;

    void deleteVirtualMachine(@NotNull VirtualMachine vm, boolean deleteFromStorage) throws AzureCmdException;

    @NotNull
    byte[] downloadRDP(@NotNull VirtualMachine vm) throws AzureCmdException;

    @NotNull
    List<StorageAccount> getStorageAccounts(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<VirtualMachineImage> getVirtualMachineImages(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<VirtualMachineSize> getVirtualMachineSizes(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<Location> getLocations(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<AffinityGroup> getAffinityGroups(@NotNull String subscriptionId) throws AzureCmdException;

    @NotNull
    List<VirtualNetwork> getVirtualNetworks(@NotNull String subscriptionId) throws AzureCmdException;

    void createStorageAccount(@NotNull StorageAccount storageAccount) throws AzureCmdException;

    void createCloudService(@NotNull CloudService cloudService) throws AzureCmdException;

    void createVirtualMachine(@NotNull VirtualMachine virtualMachine, @NotNull VirtualMachineImage vmImage,
                              @NotNull StorageAccount storageAccount, @NotNull String virtualNetwork,
                              @NotNull String username, @NotNull String password, @NotNull byte[] certificate)
            throws AzureCmdException;

    void createVirtualMachine(@NotNull VirtualMachine virtualMachine, @NotNull VirtualMachineImage vmImage,
                              @NotNull String mediaLocation, @NotNull String virtualNetwork,
                              @NotNull String username, @NotNull String password, @NotNull byte[] certificate)
            throws AzureCmdException;

    @NotNull
    StorageAccount refreshStorageAccountInformation(@NotNull StorageAccount storageAccount) throws AzureCmdException;

    String createServiceCertificate(@NotNull String subscriptionId, @NotNull String serviceName,
                                    @NotNull byte[] data, @NotNull String password)
            throws AzureCmdException;

    void deleteStorageAccount(@NotNull StorageAccount storageAccount) throws AzureCmdException;
}