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
package com.microsoftopentechnologies.intellij.components;

import com.google.gson.Gson;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ArrayUtil;
import com.microsoftopentechnologies.intellij.helpers.IDEHelperImpl;
import com.microsoftopentechnologies.intellij.helpers.UIHelperImpl;
import com.microsoftopentechnologies.intellij.serviceexplorer.NodeActionsMap;
import com.microsoftopentechnologies.intellij.wizards.activityConfiguration.AddServiceWizard;
import com.microsoftopentechnologies.tooling.msservices.components.AppSettingsNames;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.components.PluginComponent;
import com.microsoftopentechnologies.tooling.msservices.components.PluginSettings;
import com.microsoftopentechnologies.tooling.msservices.helpers.StringHelper;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.regex.Pattern;

public class MSOpenTechToolsApplication extends ApplicationComponent.Adapter implements PluginComponent {
    private static final String PLUGIN_FILE_CODE = "//376d91c0-5633-4523-b012-f2d9ecfbe6c7";
    private static final String MOBILE_SERVICE_CODE = "//010fa0c4-5af1-4f81-95c1-720d9fab8d96";
    private static final String NOTIFICATION_HUBS_CODE = "//46cca6b7-ff7d-4e05-9ef2-d7eb4798222e";
    ;
    private static final String OUTLOOK_SERVICES_CODE = "//fa684d69-70b3-41ec-83ff-2f8fa77aeeba";
    private static final String FILE_SERVICES_CODE = "//1073bed4-78c3-4b4a-8a4d-ad874a286d86";
    private static final String LIST_SERVICES_CODE = "//6695fd94-10cc-4274-b5df-46a3bc63a33d";
    private static final String ONENOTE_SERVICES_CODE = "//657555dc-6167-466a-9536-071307770d46";


    private static final VirtualFileListener vfl = getVirtualFileListener();

    private static MSOpenTechToolsApplication current = null;
    private PluginSettings settings;

    // TODO: This needs to be the plugin ID from plugin.xml somehow.
    public static final String PLUGIN_ID = "com.microsoftopentechnologies.intellij";

    public MSOpenTechToolsApplication() {
    }

    public static MSOpenTechToolsApplication getCurrent() {
        return current;
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "MSOpenTechTools";
    }

    @Override
    public void initComponent() {
        // save the object instance
        current = this;

        DefaultLoader.setPluginComponent(this);
        DefaultLoader.setUiHelper(new UIHelperImpl());
        DefaultLoader.setIdeHelper(new IDEHelperImpl());
        DefaultLoader.setNode2Actions(NodeActionsMap.node2Actions);

        // load up the plugin settings
        try {
            loadPluginSettings();
        } catch (IOException e) {
            DefaultLoader.getUIHelper().showException("An error occurred while attempting to load " +
                    "settings for the MSOpenTech Tools plugin.", e);
        }

        cleanTempData(PropertiesComponent.getInstance());

        VirtualFileManager.getInstance().addVirtualFileListener(vfl);
    }

    @Override
    public void disposeComponent() {
        VirtualFileManager.getInstance().removeVirtualFileListener(vfl);
    }

    @Override
    public PluginSettings getSettings() {
        return settings;
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    private void loadPluginSettings() throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(
                            MSOpenTechToolsApplication.class.getResourceAsStream("/settings.json")));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            Gson gson = new Gson();
            settings = gson.fromJson(sb.toString(), PluginSettings.class);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void cleanTempData(PropertiesComponent propComp) {
        // check the plugin version stored in the properties; if it
        // doesn't match with the current plugin version then we clear
        // all stored options
        // TODO: The authentication tokens are stored with the subscription id appended as a
        // suffix to AZURE_AUTHENTICATION_TOKEN. So clearing that requires that we enumerate the
        // current subscriptions and iterate over that list to clear the auth tokens for those
        // subscriptions.
        PropertiesComponent properties = PropertiesComponent.getInstance();
        String currentPluginVersion = properties.getValue(AppSettingsNames.CURRENT_PLUGIN_VERSION);

        if (StringHelper.isNullOrWhiteSpace(currentPluginVersion) ||
                !getSettings().getPluginVersion().equals(currentPluginVersion)) {

            String[] settings = new String[]{
                    AppSettingsNames.AZURE_AUTHENTICATION_MODE,
                    AppSettingsNames.AZURE_AUTHENTICATION_TOKEN,
                    AppSettingsNames.O365_AUTHENTICATION_TOKEN,
                    AppSettingsNames.SUBSCRIPTION_FILE
            };

            for (String setting : settings) {
                properties.unsetValue(setting);
            }
        }

        // save the current plugin version
        properties.setValue(AppSettingsNames.CURRENT_PLUGIN_VERSION, getSettings().getPluginVersion());
    }

    private static VirtualFileListener getVirtualFileListener() {
        return new VirtualFileListener() {
            @Override
            public void propertyChanged(@NotNull VirtualFilePropertyEvent virtualFilePropertyEvent) {
            }

            @Override
            public void contentsChanged(@NotNull VirtualFileEvent virtualFileEvent) {
            }

            @Override
            public void fileCreated(@NotNull final VirtualFileEvent virtualFileEvent) {
            }

            @Override
            public void fileDeleted(@NotNull VirtualFileEvent virtualFileEvent) {
            }

            @Override
            public void fileMoved(@NotNull VirtualFileMoveEvent virtualFileMoveEvent) {
            }

            @Override
            public void fileCopied(@NotNull VirtualFileCopyEvent virtualFileCopyEvent) {
            }

            @Override
            public void beforePropertyChange(@NotNull VirtualFilePropertyEvent virtualFilePropertyEvent) {
            }

            @Override
            public void beforeContentsChange(@NotNull VirtualFileEvent virtualFileEvent) {
                if (virtualFileEvent.isFromSave()) {
                    final VirtualFile vf = virtualFileEvent.getFile();
                    Object requestor = virtualFileEvent.getRequestor();

                    if ("java".equals(vf.getExtension()) && (requestor instanceof FileDocumentManagerImpl)) {
                        FileDocumentManagerImpl fdm = (FileDocumentManagerImpl) requestor;
                        final Document document = fdm.getDocument(vf);

                        if (document != null) {
                            final int codeLineStart = document.getLineStartOffset(0);
                            int codeLineEnd = document.getLineEndOffset(0);
                            TextRange codeLineRange = new TextRange(codeLineStart, codeLineEnd);
                            String codeLine = document.getText(codeLineRange);

                            String[] codeList = codeLine.split(Pattern.quote("^"));

                            final boolean isMobileService = ArrayUtil.contains(MOBILE_SERVICE_CODE, codeList);
                            final boolean isNotificationHub = ArrayUtil.contains(NOTIFICATION_HUBS_CODE, codeList);
                            final boolean isOutlookServices = ArrayUtil.contains(OUTLOOK_SERVICES_CODE, codeList);
                            final boolean isFileServices = ArrayUtil.contains(FILE_SERVICES_CODE, codeList);
                            final boolean isListServices = ArrayUtil.contains(LIST_SERVICES_CODE, codeList);
                            final boolean isOneNoteServices = ArrayUtil.contains(ONENOTE_SERVICES_CODE, codeList);

                            if (ArrayUtil.contains(PLUGIN_FILE_CODE, codeList)) {
                                final int packageLineStart = document.getLineStartOffset(1);

                                CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        document.deleteString(codeLineStart, packageLineStart);
                                    }
                                });

                                fdm.saveDocument(document);

                                if (isMobileService || isNotificationHub || isOutlookServices || isFileServices || isListServices || isOneNoteServices) {

                                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            Collection<Project> projects = ProjectLocator.getInstance().getProjectsForFile(vf);

                                            if (projects.size() == 1) {
                                                AddServiceWizard.run(
                                                        (Project) projects.toArray()[0],
                                                        ModuleUtil.findModuleForFile(vf, (Project) projects.toArray()[0]),
                                                        vf.getNameWithoutExtension(),
                                                        isMobileService,
                                                        isNotificationHub,
                                                        isOutlookServices,
                                                        isFileServices,
                                                        isListServices,
                                                        isOneNoteServices);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void beforeFileDeletion(@NotNull VirtualFileEvent virtualFileEvent) {
            }

            @Override
            public void beforeFileMovement(@NotNull VirtualFileMoveEvent virtualFileMoveEvent) {
            }
        };
    }
}