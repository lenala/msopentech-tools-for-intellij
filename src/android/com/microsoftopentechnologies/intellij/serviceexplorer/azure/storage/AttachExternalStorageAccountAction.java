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

package com.microsoftopentechnologies.intellij.serviceexplorer.azure.storage;

import com.microsoftopentechnologies.intellij.forms.ExternalStorageAccountForm;
import com.microsoftopentechnologies.intellij.helpers.UIHelperImpl;
import com.microsoftopentechnologies.tooling.msservices.helpers.ExternalStorageHelper;
import com.microsoftopentechnologies.tooling.msservices.helpers.Name;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.sdk.StorageClientSDKManagerImpl;
import com.microsoftopentechnologies.tooling.msservices.model.storage.ClientStorageAccount;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.NodeActionEvent;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.NodeActionListener;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.azure.storage.ExternalStorageNode;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.azure.storage.StorageModule;

import java.awt.*;

@Name("Attach external storage account")
public class AttachExternalStorageAccountAction extends NodeActionListener {
    private final StorageModule storageModule;

    public AttachExternalStorageAccountAction(StorageModule storageModule) {
        this.storageModule = storageModule;
    }

    @Override
    public void actionPerformed(NodeActionEvent e) {
        final ExternalStorageAccountForm form = new ExternalStorageAccountForm();
        form.setTitle("Attach External Storage Account");
        UIHelperImpl.packAndCenterJDialog(form);

        form.setOnFinish(new Runnable() {
            @Override
            public void run() {
                ClientStorageAccount storageAccount = form.getStorageAccount();

                storageModule.addChildNode(new ExternalStorageNode(storageModule, storageAccount));

                ExternalStorageHelper.add(storageAccount);

                form.setCursor(Cursor.getDefaultCursor());

            }
        });

        form.setVisible(true);
    }
}
