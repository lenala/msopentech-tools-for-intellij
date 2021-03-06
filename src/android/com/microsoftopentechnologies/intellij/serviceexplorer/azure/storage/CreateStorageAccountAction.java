/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoftopentechnologies.intellij.serviceexplorer.azure.storage;

import com.intellij.openapi.project.Project;
import com.microsoftopentechnologies.intellij.helpers.UIHelperImpl;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.intellij.forms.CreateStorageAccountForm;
import com.microsoftopentechnologies.tooling.msservices.helpers.Name;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureAuthenticationMode;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.rest.AzureRestAPIManager;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.rest.AzureRestAPIManagerImpl;
import com.microsoftopentechnologies.tooling.msservices.model.ms.Subscription;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.NodeActionEvent;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.NodeActionListener;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.azure.storage.StorageModule;

import java.util.ArrayList;

@Name("Create Storage Account")
public class CreateStorageAccountAction extends NodeActionListener {

    private StorageModule storageModule;

    public CreateStorageAccountAction(StorageModule storageModule) {
        this.storageModule = storageModule;
    }

    @Override
    public void actionPerformed(NodeActionEvent e) {
        // check if we have a valid subscription handy
        AzureRestAPIManager apiManager = AzureRestAPIManagerImpl.getManager();

        if (apiManager.getAuthenticationMode() == AzureAuthenticationMode.Unknown) {
            DefaultLoader.getUIHelper().showException("Please configure an Azure subscription by right-clicking on the \"Azure\" " +
                    "node and selecting \"Manage subscriptions\".", null, "No Azure subscription found");
            return;
        }

        try {
            ArrayList<Subscription> subscriptions = apiManager.getSubscriptionList();

            if (subscriptions == null || subscriptions.isEmpty()) {
                DefaultLoader.getUIHelper().showException("No active Azure subscription was found. Please enable one more Azure " +
                                "subscriptions by right-clicking on the \"Azure\" " +
                                "node and selecting \"Manage subscriptions\".",
                        null, "No active Azure subscription found");
                return;
            }
        } catch (AzureCmdException e1) {
            DefaultLoader.getUIHelper().showException("An error occurred while creating the storage account.", e1);
        }

        CreateStorageAccountForm createStorageAccountForm = new CreateStorageAccountForm();
        createStorageAccountForm.fillFields(null, (Project) storageModule.getProject());
        UIHelperImpl.packAndCenterJDialog(createStorageAccountForm);

            createStorageAccountForm.setOnCreate(new Runnable() {
                @Override
                public void run() {
                    try {
                        storageModule.refreshItems();
                    } catch (AzureCmdException ex) {
                        DefaultLoader.getUIHelper().showException("Error refreshing storage accounts", ex, "Service Explorer", false, true);
                    }
                }
            });
        createStorageAccountForm.setVisible(true);
    }
}
