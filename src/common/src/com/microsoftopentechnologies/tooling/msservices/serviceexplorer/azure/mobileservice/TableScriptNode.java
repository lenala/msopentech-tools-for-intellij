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

package com.microsoftopentechnologies.tooling.msservices.serviceexplorer.azure.mobileservice;

import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.rest.AzureRestAPIManagerImpl;
import com.microsoftopentechnologies.tooling.msservices.model.ms.Script;
import com.microsoftopentechnologies.tooling.msservices.model.ms.MobileService;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.Node;
import com.microsoftopentechnologies.tooling.msservices.serviceexplorer.NodeActionEvent;

public class TableScriptNode extends ScriptNodeBase {
    public static final String ICON_PATH = "script.png";
    protected Script script;

    public TableScriptNode(Node parent, Script script) {
        super(script.getName(), script.toString(), parent, ICON_PATH, false);
        this.script = script;
    }

    @Override
    protected void onNodeClick(NodeActionEvent event) {
        onNodeClickInternal(script);
    }

    @Override
    protected void downloadScript(MobileService mobileService, String scriptName, String localFilePath) throws AzureCmdException {
        AzureRestAPIManagerImpl.getManager().downloadTableScript(
                mobileService.getSubcriptionId(),
                mobileService.getName(),
                scriptName,
                localFilePath);
    }

    public Script getScript() {
        return script;
    }
}
