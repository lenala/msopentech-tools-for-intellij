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

package com.microsoftopentechnologies.tooling.msservices.helpers.azure.sdk;

import com.microsoft.windowsazure.credentials.SubscriptionCloudCredentials;

import java.util.Map;

public class EmptyCloudCredentials extends SubscriptionCloudCredentials {
    private final String subscriptionId;

    public EmptyCloudCredentials(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    @Override
    public String getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public <T> void applyConfig(String profile, Map<String, Object> properties) {
        // all of our logic is in the AuthTokenRequestFilter and the AzureSDKManagerADAuthDecorator
        // classes; this is a "token" cloud credentials implementation (pun intended).
    }
}
