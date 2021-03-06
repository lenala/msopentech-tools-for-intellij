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

package com.microsoftopentechnologies.intellij.wizards.activityConfiguration;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.wizard.WizardModel;
import com.microsoft.directoryservices.Application;
import com.microsoftopentechnologies.tooling.msservices.helpers.graph.ServicePermissionEntry;
import com.microsoftopentechnologies.tooling.msservices.model.ms.MobileService;

import java.util.ArrayList;
import java.util.List;

public class AddServiceWizardModel extends WizardModel {
    private final Project project;
    private final Module module;
    private final String activityName;
    private final List<ServiceType> serviceTypes;
    private String senderId;
    private String connectionString;
    private String hubName;
    private boolean isOutlookServices;
    private boolean isFileServices;
    private boolean isListServices;
    private final boolean isOneNoteServices;
    private MobileService mobileService;

    private Application officeApp;
    private List<ServicePermissionEntry> officePermissions;
    private String clientId;

    public AddServiceWizardModel(Project project, Module module, String activityName, boolean isMobileServiceSelected,
                                 boolean isNotificationHubSelected, boolean isOutlookServices, boolean isFileServices,
                                 boolean isListServices, boolean isOneNoteServices) {
        super(ApplicationNamesInfo.getInstance().getFullProductName() + " - Add Microsoft Service Wizard");
        this.project = project;
        this.module = module;
        this.activityName = activityName;
        this.serviceTypes = new ArrayList<ServiceType>();
        this.isOutlookServices = isOutlookServices;
        this.isFileServices = isFileServices;
        this.isListServices = isListServices;
        this.isOneNoteServices = isOneNoteServices;

        if (isMobileServiceSelected) {
            this.serviceTypes.add(ServiceType.AzureMobileServices);
        }

        if (isNotificationHubSelected) {
            this.serviceTypes.add(ServiceType.NotificationHub);
        }

        if (isOutlookServices || isFileServices || isListServices) {
            this.serviceTypes.add(ServiceType.Office365);
        }

        if(isOneNoteServices) {
            this.serviceTypes.add(ServiceType.OneNote);
        }

        initSteps(isMobileServiceSelected, isNotificationHubSelected, isOutlookServices, isFileServices, isListServices, isOneNoteServices);
    }

    public Project getProject() {
        return project;
    }

    public Module getModule() {
        return module;
    }

    public String getActivityName() {
        return activityName;
    }

    public List<ServiceType> getServiceTypes() {
        return this.serviceTypes;
    }

    public MobileService getMobileService() {
        return this.mobileService;
    }

    public void setMobileService(MobileService mobileService) {
        this.mobileService = mobileService;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getHubName() {
        return hubName;
    }

    public void setHubName(String hubName) {
        this.hubName = hubName;
    }

    public boolean isOutlookServices() {
        return isOutlookServices;
    }

    public boolean isFileServices() {
        return isFileServices;
    }

    public boolean isListServices() {
        return isListServices;
    }

    public boolean isOneNoteServices() {
        return isOneNoteServices;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }


    public List<ServicePermissionEntry> getOfficePermissions() {
        return officePermissions;
    }

    public void setOfficePermissions(List<ServicePermissionEntry> officePermissions) {
        this.officePermissions = officePermissions;
    }

    public Application getOfficeApp() {
        return officeApp;
    }

    public void setOfficeApp(Application officeApp) {
        this.officeApp = officeApp;
    }

    private void initSteps(boolean isMobileServiceSelected, boolean isNotificationHubSelected,
                           boolean isOutlookServices, boolean isFileServices, boolean isListServices,
                           boolean isOneNoteServices) {
        String title = "";
        if (isMobileServiceSelected) {
            title = "Azure Services";
            add(new AzureMobileServiceStep(title, this));
        }

        if (isNotificationHubSelected) {
            title = "Azure Services";
            add(new NotificationHubStep(title, this));
        }

        if (isOutlookServices || isFileServices || isListServices) {
            title = "Office 365";
            add(new Office365Step(title, this));
        }

        if(isOneNoteServices) {
            title = "OneNote";
            add(new OneNoteStep(title, this));
        }
        add(new SummaryStep(title, this));
    }

}