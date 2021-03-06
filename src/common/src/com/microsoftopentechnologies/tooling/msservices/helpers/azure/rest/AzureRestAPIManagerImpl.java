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
package com.microsoftopentechnologies.tooling.msservices.helpers.azure.rest;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoftopentechnologies.aad.adal4j.AuthenticationResult;
import com.microsoftopentechnologies.tooling.msservices.components.AppSettingsNames;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.helpers.NoSubscriptionException;
import com.microsoftopentechnologies.tooling.msservices.helpers.StringHelper;
import com.microsoftopentechnologies.tooling.msservices.helpers.XmlHelper;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureAuthenticationMode;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.rest.model.*;
import com.microsoftopentechnologies.tooling.msservices.model.ms.*;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import sun.misc.BASE64Encoder;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

public class AzureRestAPIManagerImpl implements AzureRestAPIManager {
    // singleton API manager instance
    private static AzureRestAPIManagerImpl apiManager = null;

    // This is the authentication token.
    // TODO: Should we store this encrypted in memory?
    // TODO: Implement offline encrypted caching so that user doesn't have to re-authenticate every time they run.
    private AuthenticationResult authenticationToken;
    private ReentrantLock authenticationTokenLock = new ReentrantLock();

    // list of azure subscriptions
    private ArrayList<Subscription> subscriptions;
    private ReentrantLock subscriptionsLock = new ReentrantLock();

    // cache of authentication tokens by azure subscription ID
    private Map<String, AuthenticationResult> authenticationTokenSubscriptionMap =
            new HashMap<String, AuthenticationResult>();
    private ReentrantLock authenticationTokenSubscriptionMapLock = new ReentrantLock();

    private AzureRestAPIManagerImpl() {
    }

    public static AzureRestAPIManager getManager() {
        if (apiManager == null) {
            apiManager = new AzureRestAPIManagerImpl();
        }

        return apiManager;
    }

    @Override
    public AzureAuthenticationMode getAuthenticationMode() {
        return AzureAuthenticationMode.valueOf(
                DefaultLoader.getIdeHelper().getProperty(
                        AppSettingsNames.AZURE_AUTHENTICATION_MODE,
                        AzureAuthenticationMode.Unknown.toString()));
    }

    @Override
    public void setAuthenticationMode(AzureAuthenticationMode azureAuthenticationMode) {
        DefaultLoader.getIdeHelper().setProperty(
                AppSettingsNames.AZURE_AUTHENTICATION_MODE,
                azureAuthenticationMode.toString());
    }

    public AuthenticationResult getAuthenticationTokenForSubscription(String subscriptionId) {
        // build key for the properties cache
        String key = AppSettingsNames.AZURE_AUTHENTICATION_TOKEN + "_" + subscriptionId;

        // check if the token is already available in our cache
        if (authenticationTokenSubscriptionMap.containsKey(key)) {
            return authenticationTokenSubscriptionMap.get(key);
        }

        String json = DefaultLoader.getIdeHelper().getProperty(key);
        if (!StringHelper.isNullOrWhiteSpace(json)) {
            Gson gson = new Gson();
            AuthenticationResult token = gson.fromJson(json, AuthenticationResult.class);

            // save the token to the cache
            authenticationTokenSubscriptionMapLock.lock();
            try {
                authenticationTokenSubscriptionMap.put(key, token);
            } finally {
                authenticationTokenSubscriptionMapLock.unlock();
            }
        }

        return authenticationTokenSubscriptionMap.get(key);
    }

    public void setAuthenticationTokenForSubscription(
            String subscriptionId,
            AuthenticationResult authenticationToken) {
        // build key for the properties cache
        String key = AppSettingsNames.AZURE_AUTHENTICATION_TOKEN + "_" + subscriptionId;

        authenticationTokenSubscriptionMapLock.lock();
        try {
            // update the token in the cache
            if (authenticationToken == null) {
                if (authenticationTokenSubscriptionMap.containsKey(key)) {
                    authenticationTokenSubscriptionMap.remove(key);
                }
            } else {
                authenticationTokenSubscriptionMap.put(key, authenticationToken);
            }

            // save the token in persistent storage
            String json = "";

            if (authenticationToken != null) {
                Gson gson = new Gson();
                json = gson.toJson(authenticationToken, AuthenticationResult.class);
            }

            DefaultLoader.getIdeHelper().setProperty(key, json);
        } finally {
            authenticationTokenSubscriptionMapLock.unlock();
        }
    }

    @Override
    public AuthenticationResult getAuthenticationToken() {
        if (authenticationToken == null) {
            String json = DefaultLoader.getIdeHelper().getProperty(AppSettingsNames.AZURE_AUTHENTICATION_TOKEN);

            if (!StringHelper.isNullOrWhiteSpace(json)) {
                Gson gson = new Gson();
                authenticationTokenLock.lock();

                try {
                    authenticationToken = gson.fromJson(json, AuthenticationResult.class);
                } finally {
                    authenticationTokenLock.unlock();
                }
            }
        }

        return authenticationToken;
    }

    @Override
    public void setAuthenticationToken(AuthenticationResult authenticationToken) {
        authenticationTokenLock.lock();

        try {
            this.authenticationToken = authenticationToken;
            String json = "";

            if (this.authenticationToken != null) {
                Gson gson = new Gson();
                json = gson.toJson(this.authenticationToken, AuthenticationResult.class);
            }

            DefaultLoader.getIdeHelper().setProperty(AppSettingsNames.AZURE_AUTHENTICATION_TOKEN, json);
        } finally {
            authenticationTokenLock.unlock();
        }
    }

    @Override
    public void clearSubscriptions() throws AzureCmdException {
        DefaultLoader.getIdeHelper().unsetProperty(AppSettingsNames.SUBSCRIPTION_FILE);
        subscriptionsLock.lock();

        try {
            if (subscriptions != null) {
                subscriptions.clear();
                subscriptions = null;
            }
        } finally {
            subscriptionsLock.unlock();
        }
    }

    @Override
    public void clearAuthenticationTokens() {
        if (subscriptions != null) {
            for (Subscription subscription : subscriptions) {
                setAuthenticationTokenForSubscription(subscription.getId().toString(), null);
            }
        }

        setAuthenticationToken(null);
    }

    @Override
    public ArrayList<Subscription> getSubscriptionList() throws AzureCmdException {
        try {
            AzureAuthenticationMode mode = getAuthenticationMode();
            ArrayList<Subscription> fullList = null;

            if (mode == AzureAuthenticationMode.SubscriptionSettings) {
                fullList = getSubscriptionListFromCert();
            } else if (mode == AzureAuthenticationMode.ActiveDirectory) {
                fullList = getSubscriptionListFromToken();
            }

            if (fullList != null) {
                ArrayList<Subscription> ret = new ArrayList<Subscription>();
                for (Subscription subscription : fullList) {
                    if (subscription.isSelected())
                        ret.add(subscription);
                }

                return ret;
            }

            return null;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting subscription list", e);
        }
    }


    @Override
    public ArrayList<Subscription> getFullSubscriptionList() throws AzureCmdException {
        try {
            AzureAuthenticationMode mode = getAuthenticationMode();

            if (mode == AzureAuthenticationMode.SubscriptionSettings) {
                return getSubscriptionListFromCert();
            } else if (mode == AzureAuthenticationMode.ActiveDirectory) {
                return getSubscriptionListFromToken();
            }

            return null;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting subscription list", e);
        }
    }

    public void setSelectedSubscriptions(List<UUID> selectedList) throws AzureCmdException {
        try {
            AzureAuthenticationMode mode = getAuthenticationMode();

            if (mode == AzureAuthenticationMode.SubscriptionSettings) {
                String subscriptionFile = DefaultLoader.getIdeHelper().getProperty(AppSettingsNames.SUBSCRIPTION_FILE, "");

                NodeList subscriptionList = (NodeList) XmlHelper.getXMLValue(subscriptionFile, "//Subscription", XPathConstants.NODESET);
                for (int i = 0; i < subscriptionList.getLength(); i++) {
                    UUID id = UUID.fromString(XmlHelper.getAttributeValue(subscriptionList.item(i), "Id"));
                    Node node = subscriptionList.item(i).getAttributes().getNamedItem("Selected");

                    if (node == null) {
                        node = subscriptionList.item(i).getOwnerDocument().createAttribute("Selected");
                    }

                    node.setNodeValue(selectedList.contains(id) ? "true" : "false");
                    subscriptionList.item(i).getAttributes().setNamedItem(node);
                }

                if (subscriptionList.getLength() > 0) {
                    String savedXml = XmlHelper.saveXmlToStreamWriter(subscriptionList.item(0).getOwnerDocument());
                    DefaultLoader.getIdeHelper().setProperty(AppSettingsNames.SUBSCRIPTION_FILE, savedXml);
                }
            } else if (mode == AzureAuthenticationMode.ActiveDirectory) {
                if (subscriptions != null) {
                    for (Subscription subscription : subscriptions) {
                        subscription.setSelected(selectedList.contains(subscription.getId()));
                    }
                }

                ArrayList<String> selectedIds = new ArrayList<String>();

                for (UUID uuid : selectedList) {
                    selectedIds.add(uuid.toString());
                }

                DefaultLoader.getIdeHelper().setProperty(AppSettingsNames.SELECTED_SUBSCRIPTIONS, StringUtils.join(selectedIds, ","));
            }
        } catch (Exception e) {
            throw new AzureCmdException("Error getting subscription list", e);
        }
    }

    public ArrayList<Subscription> getSubscriptionListFromCert() throws SAXException, ParserConfigurationException, XPathExpressionException, IOException {
        String subscriptionFile = DefaultLoader.getIdeHelper().getProperty(AppSettingsNames.SUBSCRIPTION_FILE, "");

        if (subscriptionFile.trim().isEmpty()) {
            return null;
        }

        NodeList subscriptionList = (NodeList) XmlHelper.getXMLValue(subscriptionFile, "//Subscription", XPathConstants.NODESET);

        ArrayList<Subscription> list = new ArrayList<Subscription>();

        for (int i = 0; i < subscriptionList.getLength(); i++) {
            Subscription subscription = new Subscription();
            subscription.setName(XmlHelper.getAttributeValue(subscriptionList.item(i), "Name"));
            subscription.setId(UUID.fromString(XmlHelper.getAttributeValue(subscriptionList.item(i), "Id")));
            String selected = XmlHelper.getAttributeValue(subscriptionList.item(i), "Selected");
            subscription.setSelected(selected == null || selected.equals("true"));

            list.add(subscription);
        }

        return list;
    }

    public void refreshSubscriptionListFromToken() throws IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ExecutionException, ParserConfigurationException, InterruptedException, AzureCmdException, SAXException, NoSubscriptionException, KeyStoreException, XPathExpressionException, KeyManagementException {

        ArrayList<UUID> selectedIds = new ArrayList<UUID>();
        String selectedSubscriptions = null;

        if (DefaultLoader.getIdeHelper().isPropertySet(AppSettingsNames.SELECTED_SUBSCRIPTIONS)) {
            selectedSubscriptions = DefaultLoader.getIdeHelper().getProperty(AppSettingsNames.SELECTED_SUBSCRIPTIONS, "");
        }

        if (selectedSubscriptions != null && !selectedSubscriptions.isEmpty()) {
            for (String id : selectedSubscriptions.split(",")) {
                selectedIds.add(UUID.fromString(id));
            }
        }

        String subscriptionXml = AzureRestAPIHelper.getRestApiCommand("subscriptions", null);
        DefaultLoader.getIdeHelper().setProperty(AppSettingsNames.SUBSCRIPTION_FILE, subscriptionXml);
        NodeList subscriptionList = (NodeList) XmlHelper.getXMLValue(subscriptionXml, "//Subscription", XPathConstants.NODESET);

        subscriptionsLock.lock();

        try {
            subscriptions = new ArrayList<Subscription>();

            for (int i = 0; i < subscriptionList.getLength(); i++) {
                Subscription subscription = new Subscription();
                subscription.setName(XmlHelper.getChildNodeValue(subscriptionList.item(i), "SubscriptionName"));
                subscription.setId(UUID.fromString(XmlHelper.getChildNodeValue(subscriptionList.item(i), "SubscriptionID")));
                subscription.setTenantId(XmlHelper.getChildNodeValue(subscriptionList.item(i), "AADTenantID"));
                subscription.setSelected(selectedSubscriptions == null || selectedIds.contains(subscription.getId()));

                subscriptions.add(subscription);
            }
        } finally {
            subscriptionsLock.unlock();
        }
    }

    public ArrayList<Subscription> getSubscriptionListFromToken() throws AzureCmdException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ExecutionException, ParserConfigurationException, InterruptedException, SAXException, NoSubscriptionException, KeyStoreException, XPathExpressionException, KeyManagementException {
        if (subscriptions == null) {
            refreshSubscriptionListFromToken();
            assert subscriptions != null;
        }

        return subscriptions;
    }

    public Subscription getSubscriptionFromId(final String subscriptionId) throws SAXException, ParserConfigurationException, XPathExpressionException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ExecutionException, InterruptedException, KeyManagementException, KeyStoreException, AzureCmdException, NoSubscriptionException {
        ArrayList<Subscription> subscriptions = null;
        AzureAuthenticationMode mode = getAuthenticationMode();

        if (mode == AzureAuthenticationMode.SubscriptionSettings) {
            subscriptions = getSubscriptionListFromCert();
        } else if (mode == AzureAuthenticationMode.ActiveDirectory) {
            subscriptions = getSubscriptionListFromToken();
        }

        if (subscriptions == null) {
            return null;
        }

        final UUID sid = UUID.fromString(subscriptionId);

        return Iterables.find(subscriptions, new Predicate<Subscription>() {
            @Override
            public boolean apply(Subscription subscription) {
                return subscription.getId().compareTo(sid) == 0;
            }
        });
    }

    @Override
    public void loadSubscriptionFile(String subscriptionFile) throws AzureCmdException {
        // update the auth mode and clear out the subscriptions xml
        setAuthenticationMode(AzureAuthenticationMode.SubscriptionSettings);

        try {
            apiManager.clearSubscriptions();
            AzureRestAPIHelper.importSubscription(new File(subscriptionFile));
        } catch (AzureCmdException ex) {
            setAuthenticationMode(AzureAuthenticationMode.Unknown);

            throw ex;
        }
    }

    @Override
    public List<MobileService> getServiceList(UUID subscriptionId) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices", subscriptionId.toString());

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Type type = new TypeToken<ArrayList<MobileServiceData>>() {
            }.getType();
            List<MobileServiceData> tempRes = new Gson().fromJson(json, type);

            List<MobileService> res = new ArrayList<MobileService>();

            for (MobileServiceData item : tempRes) {
                MobileService ser = new MobileService();

                ser.setName(item.getName());
                ser.setType(item.getType());
                ser.setState(item.getState());
                ser.setSelfLink(item.getSelflink());
                ser.setAppUrl(item.getApplicationUrl());
                ser.setAppKey(item.getApplicationKey());
                ser.setMasterKey(item.getMasterKey());
                ser.setWebspace(item.getWebspace());
                ser.setRegion(item.getRegion());
                ser.setMgmtPortalLink(item.getManagementPortalLink());
                ser.setSubcriptionId(subscriptionId);

                if (item.getPlatform() != null && item.getPlatform().equals("dotNet")) {
                    ser.setRuntime(MobileService.NET_RUNTIME);
                } else {
                    ser.setRuntime(MobileService.NODE_RUNTIME);
                }

                for (MobileServiceData.Table table : item.getTables()) {
                    Table t = new Table();
                    t.setName(table.getName());
                    t.setSelfLink(table.getSelflink());
                    ser.getTables().add(t);
                }

                res.add(ser);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting service list", e);
        }
    }

    @Override
    public List<String> getLocations(UUID subscriptionId) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/regions", subscriptionId.toString());

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());


            Type type = new TypeToken<ArrayList<RegionData>>() {
            }.getType();
            List<RegionData> tempRes = new Gson().fromJson(json, type);
            List<String> res = new ArrayList<String>();

            for (RegionData item : tempRes) {
                res.add(item.getRegion());
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting region list", e);
        }
    }

    @Override
    public List<SqlDb> getSqlDb(UUID subscriptionId, SqlServer server) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/sqlservers/servers/%s/databases?contentview=generic", subscriptionId.toString(), server.getName());
            String xml = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            List<SqlDb> res = new ArrayList<SqlDb>();
            NodeList nl = (NodeList) XmlHelper.getXMLValue(xml, "//ServiceResource", XPathConstants.NODESET);

            for (int i = 0; i != nl.getLength(); i++) {

                SqlDb sqls = new SqlDb();
                sqls.setName(XmlHelper.getChildNodeValue(nl.item(i), "Name"));
                sqls.setEdition(XmlHelper.getChildNodeValue(nl.item(i), "Edition"));
                sqls.setServer(server);
                res.add(sqls);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting database list", e);
        }
    }

    @Override
    public List<SqlServer> getSqlServers(UUID subscriptionId) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/sqlservers/servers", subscriptionId.toString());
            String xml = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            List<SqlServer> res = new ArrayList<SqlServer>();

            NodeList nl = (NodeList) XmlHelper.getXMLValue(xml, "//Server", XPathConstants.NODESET);

            for (int i = 0; i != nl.getLength(); i++) {
                SqlServer sqls = new SqlServer();

                sqls.setAdmin(XmlHelper.getChildNodeValue(nl.item(i), "AdministratorLogin"));
                sqls.setName(XmlHelper.getChildNodeValue(nl.item(i), "Name"));
                sqls.setRegion(XmlHelper.getChildNodeValue(nl.item(i), "Location"));
                res.add(sqls);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting server list", e);
        }
    }

    @Override
    public void createService(UUID subscriptionId, String region, String username, String password, String serviceName, String server, String database) throws AzureCmdException {
        try {
            String path = String.format("/%s/applications", subscriptionId.toString());

            String JSONParameter;

            if (database == null || server == null) {
                String zumoServerId = UUID.randomUUID().toString().replace("-", "");
                String zumoDBId = UUID.randomUUID().toString().replace("-", "");
                String dbName = serviceName + "_db";

                JSONParameter = "{'SchemaVersion':'2012-05.1.0','Location':'" + region + "','ExternalResources':{},'InternalResources':{'ZumoMobileService':" +
                        "{'ProvisioningParameters':{'Name':'" + serviceName + "','Location':'" + region + "'},'ProvisioningConfigParameters':{'Server':{'StringConcat':" +
                        "[{'ResourceReference':'ZumoSqlServer_" + zumoServerId + ".Name'},'.database.windows.net']},'Database':{'ResourceReference':'ZumoSqlDatabase_" +
                        zumoDBId + ".Name'},'AdministratorLogin':'" + username + "','AdministratorLoginPassword':'" + password + "'},'Version':'2012-05-21.1.0'," +
                        "'Name':'ZumoMobileService','Type':'Microsoft.WindowsAzure.MobileServices.MobileService'},'ZumoSqlServer_" + zumoServerId +
                        "':{'ProvisioningParameters':{'AdministratorLogin':'" + username + "','AdministratorLoginPassword':'" + password + "','Location':'" + region +
                        "'},'ProvisioningConfigParameters':{'FirewallRules':[{'Name':'AllowAllWindowsAzureIps','StartIPAddress':'0.0.0.0','EndIPAddress':'0.0.0.0'}]}," +
                        "'Version':'1.0','Name':'ZumoSqlServer_" + zumoServerId + "','Type':'Microsoft.WindowsAzure.SQLAzure.Server'},'ZumoSqlDatabase_" + zumoDBId +
                        "':{'ProvisioningParameters':{'Name':'" + dbName + "','Edition':'WEB','MaxSizeInGB':'1','DBServer':{'ResourceReference':'ZumoSqlServer_" +
                        zumoServerId + ".Name'},'CollationName':'SQL_Latin1_General_CP1_CI_AS'},'Version':'1.0','Name':'ZumoSqlDatabase_" + zumoDBId +
                        "','Type':'Microsoft.WindowsAzure.SQLAzure.DataBase'}}}";
            } else {
                String zumoServerId = UUID.randomUUID().toString().replace("-", "");
                String zumoDBId = UUID.randomUUID().toString().replace("-", "");

                JSONParameter = "{'SchemaVersion':'2012-05.1.0','Location':'West US','ExternalResources':{'ZumoSqlServer_" + zumoServerId + "':{'Name':'ZumoSqlServer_" + zumoServerId
                        + "'," + "'Type':'Microsoft.WindowsAzure.SQLAzure.Server','URI':'https://management.core.windows.net:8443/" + subscriptionId.toString()
                        + "/services/sqlservers/servers/" + server + "'}," + "'ZumoSqlDatabase_" + zumoDBId + "':{'Name':'ZumoSqlDatabase_" + zumoDBId +
                        "','Type':'Microsoft.WindowsAzure.SQLAzure.DataBase'," + "'URI':'https://management.core.windows.net:8443/" + subscriptionId.toString()
                        + "/services/sqlservers/servers/" + server + "/databases/" + database + "'}}," + "'InternalResources':{'ZumoMobileService':{'ProvisioningParameters'" +
                        ":{'Name':'" + serviceName + "','Location':'" + region + "'},'ProvisioningConfigParameters':{'Server':{'StringConcat':[{'ResourceReference':'ZumoSqlServer_"
                        + zumoServerId + ".Name'}," + "'.database.windows.net']},'Database':{'ResourceReference':'ZumoSqlDatabase_" + zumoDBId + ".Name'},'AdministratorLogin':" +
                        "'" + username + "','AdministratorLoginPassword':'" + password + "'},'Version':'2012-05-21.1.0','Name':'ZumoMobileService','Type':" +
                        "'Microsoft.WindowsAzure.MobileServices.MobileService'}}}";
            }

            String xmlParameter = String.format("<?xml version=\"1.0\" encoding=\"utf-8\"?><Application xmlns=\"http://schemas.microsoft.com/windowsazure\"><Name>%s</Name>" +
                            "<Label>%s</Label><Description>%s</Description><Configuration>%s</Configuration></Application>",
                    serviceName + "mobileservice", serviceName, serviceName, new BASE64Encoder().encode(JSONParameter.getBytes()));

            AzureRestAPIHelper.postRestApiCommand(path, xmlParameter, subscriptionId.toString(), String.format("/%s/operations/", subscriptionId.toString()), false);

            String xml = AzureRestAPIHelper.getRestApiCommand(String.format("/%s/applications/%s", subscriptionId.toString(), serviceName + "mobileservice"), subscriptionId.toString());
            NodeList statusNode = ((NodeList) XmlHelper.getXMLValue(xml, "//Application/State", XPathConstants.NODESET));

            if (!(statusNode.getLength() > 0 && statusNode.item(0).getTextContent().equals("Healthy"))) {
                deleteService(subscriptionId, serviceName);

                String errors = ((String) XmlHelper.getXMLValue(xml, "//FailureCode[text()]", XPathConstants.STRING));
                String errorMessage = ((String) XmlHelper.getXMLValue(errors, "//Message[text()]", XPathConstants.STRING));
                throw new AzureCmdException("Error creating service", errorMessage);
            }
        } catch (Throwable t) {
            if (t instanceof AzureCmdException) {
                throw (AzureCmdException) t;
            } else {
                throw new AzureCmdException("Error creating service", t);
            }
        }
    }

    @Override
    public void deleteService(UUID subscriptionId, String serviceName) {
        String mspath = String.format("/%s/services/mobileservices/mobileservices/%s?deletedata=true", subscriptionId.toString(), serviceName);

        try {
            AzureRestAPIHelper.deleteRestApiCommand(mspath, subscriptionId.toString(), String.format("/%s/operations/", subscriptionId.toString()), true);
        } catch (Throwable ignored) {
        }

        String appPath = String.format("/%s/applications/%smobileservice", subscriptionId.toString(), serviceName);

        try {
            AzureRestAPIHelper.deleteRestApiCommand(appPath, subscriptionId.toString(), String.format("/%s/operations/", subscriptionId.toString()), false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public List<Table> getTableList(UUID subscriptionId, String serviceName) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables", subscriptionId.toString(), serviceName);

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Type type = new TypeToken<ArrayList<TableData>>() {
            }.getType();
            List<TableData> tempRes = new Gson().fromJson(json, type);

            List<Table> res = new ArrayList<Table>();

            for (TableData item : tempRes) {
                Table t = new Table();
                t.setName(item.getName());
                t.setSelfLink(item.getSelflink());

                res.add(t);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting table list", e);
        }
    }

    @Override
    public void createTable(UUID subscriptionId, String serviceName, String tableName, TablePermissions permissions) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables", subscriptionId.toString(), serviceName);

            String postData = "{\"insert\":\"" + PermissionItem.getPermitionString(permissions.getInsert())
                    + "\",\"read\":\"" + PermissionItem.getPermitionString(permissions.getRead())
                    + "\",\"update\":\"" + PermissionItem.getPermitionString(permissions.getUpdate())
                    + "\",\"delete\":\"" + PermissionItem.getPermitionString(permissions.getDelete())
                    + "\",\"name\":\"" + tableName + "\",\"idType\":\"string\"}";


            AzureRestAPIHelper.postRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (Exception e) {
            throw new AzureCmdException("Error creating table", e);
        }
    }

    @Override
    public void updateTable(UUID subscriptionId, String serviceName, String tableName, TablePermissions permissions) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables/%s/permissions", subscriptionId.toString(), serviceName, tableName);

            String postData = "{\"insert\":\"" + PermissionItem.getPermitionString(permissions.getInsert())
                    + "\",\"read\":\"" + PermissionItem.getPermitionString(permissions.getRead())
                    + "\",\"update\":\"" + PermissionItem.getPermitionString(permissions.getUpdate())
                    + "\",\"delete\":\"" + PermissionItem.getPermitionString(permissions.getDelete())
                    + "\"}";

            AzureRestAPIHelper.putRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (Exception e) {
            throw new AzureCmdException("Error updating table", e);
        }
    }

    @Override
    public Table showTableDetails(UUID subscriptionId, String serviceName, String tableName) throws AzureCmdException {

        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables/%s",
                    subscriptionId.toString(), serviceName, tableName);

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());
            Gson gson = new Gson();
            TableData tempRes = gson.fromJson(json, TableData.class);

            Table t = new Table();
            t.setName(tempRes.getName());
            t.setSelfLink(tempRes.getSelflink());

            TablePermissionsData restTablePermissions = gson.fromJson(AzureRestAPIHelper.getRestApiCommand(path + "/permissions", subscriptionId.toString()), TablePermissionsData.class);

            TablePermissions tablePermissions = new TablePermissions();
            tablePermissions.setInsert(PermissionItem.getPermitionType(restTablePermissions.getInsert()));
            tablePermissions.setUpdate(PermissionItem.getPermitionType(restTablePermissions.getUpdate()));
            tablePermissions.setRead(PermissionItem.getPermitionType(restTablePermissions.getRead()));
            tablePermissions.setDelete(PermissionItem.getPermitionType(restTablePermissions.getDelete()));
            t.setTablePermissions(tablePermissions);

            Type colType = new TypeToken<ArrayList<TableColumnData>>() {
            }.getType();
            List<TableColumnData> colList = gson.fromJson(AzureRestAPIHelper.getRestApiCommand(path + "/columns", subscriptionId.toString()), colType);
            if (colList != null) {
                for (TableColumnData column : colList) {
                    Column c = new Column();
                    c.setName(column.getName());
                    c.setType(column.getType());
                    c.setSelfLink(column.getSelflink());
                    c.setIndexed(column.isIndexed());
                    c.setZumoIndex(column.isZumoIndex());

                    t.getColumns().add(c);
                }
            }


            Type scrType = new TypeToken<ArrayList<TableScriptData>>() {
            }.getType();
            List<TableScriptData> scrList = gson.fromJson(AzureRestAPIHelper.getRestApiCommand(path + "/scripts", subscriptionId.toString()), scrType);

            if (scrList != null) {
                for (TableScriptData script : scrList) {
                    Script s = new Script();

                    s.setOperation(script.getOperation());
                    s.setBytes(script.getSizeBytes());
                    s.setSelfLink(script.getSelflink());
                    s.setName(String.format("%s.%s", tempRes.getName(), script.getOperation()));

                    t.getScripts().add(s);
                }
            }

            return t;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting table data", e);
        }
    }

    @Override
    public void downloadTableScript(UUID subscriptionId, String serviceName, String scriptName, String downloadPath) throws AzureCmdException {
        try {
            String tableName = scriptName.split("\\.")[0];
            String operation = scriptName.split("\\.")[1];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables/%s/scripts/%s/code", subscriptionId.toString(), serviceName, tableName, operation);
            String script = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(downloadPath), "utf-8"));
            writer.write(script);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            //On error, create script for template
        }
    }

    @Override
    public void uploadTableScript(UUID subscriptionId, String serviceName, String scriptName, String filePath) throws AzureCmdException {
        try {
            String tableName = scriptName.split("\\.")[0];
            String operation = scriptName.split("\\.")[1];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/tables/%s/scripts/%s/code", subscriptionId.toString(), serviceName, tableName, operation);

            AzureRestAPIHelper.uploadScript(path, filePath, subscriptionId.toString());

        } catch (Exception e) {
            throw new AzureCmdException("Error upload script", e);
        }
    }

    @Override
    public List<CustomAPI> getAPIList(UUID subscriptionId, String serviceName) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/apis", subscriptionId.toString(), serviceName);

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Type type = new TypeToken<ArrayList<CustomAPIData>>() {
            }.getType();
            List<CustomAPIData> tempRes = new Gson().fromJson(json, type);

            List<CustomAPI> res = new ArrayList<CustomAPI>();

            for (CustomAPIData item : tempRes) {
                CustomAPI c = new CustomAPI();
                c.setName(item.getName());
                CustomAPIPermissions permissions = new CustomAPIPermissions();
                permissions.setPutPermission(PermissionItem.getPermitionType(item.getPut()));
                permissions.setPostPermission(PermissionItem.getPermitionType(item.getPost()));
                permissions.setGetPermission(PermissionItem.getPermitionType(item.getGet()));
                permissions.setDeletePermission(PermissionItem.getPermitionType(item.getDelete()));
                permissions.setPatchPermission(PermissionItem.getPermitionType(item.getPatch()));
                c.setCustomAPIPermissions(permissions);
                res.add(c);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting API list", e);
        }
    }

    @Override
    public void downloadAPIScript(UUID subscriptionId, String serviceName, String scriptName, String downloadPath) throws AzureCmdException {
        try {
            String apiName = scriptName.split("\\.")[0];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/apis/%s/script", subscriptionId.toString(), serviceName, apiName);
            String script = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(downloadPath), "utf-8"));
            writer.write(script);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new AzureCmdException("Error getting API list", e);
        }
    }

    @Override
    public void uploadAPIScript(UUID subscriptionId, String serviceName, String scriptName, String filePath) throws AzureCmdException {
        try {
            String apiName = scriptName.split("\\.")[0];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/apis/%s/script", subscriptionId.toString(), serviceName, apiName);

            AzureRestAPIHelper.uploadScript(path, filePath, subscriptionId.toString());
        } catch (Exception e) {
            throw new AzureCmdException("Error upload script", e);
        }
    }

    @Override
    public void createCustomAPI(UUID subscriptionId, String serviceName, String tableName, CustomAPIPermissions permissions) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/apis", subscriptionId.toString(), serviceName);

            String postData = "{\"get\":\"" + permissions.getGetPermission()
                    + "\",\"put\":\"" + permissions.getPutPermission()
                    + "\",\"post\":\"" + permissions.getPostPermission()
                    + "\",\"patch\":\"" + permissions.getPatchPermission()
                    + "\",\"delete\":\"" + permissions.getDeletePermission()
                    + "\",\"name\":\"" + tableName + "\"}";


            AzureRestAPIHelper.postRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (Exception e) {
            throw new AzureCmdException("Error creating API", e);
        }
    }

    @Override
    public void updateCustomAPI(UUID subscriptionId, String serviceName, String tableName, CustomAPIPermissions permissions) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/apis/%s", subscriptionId.toString(), serviceName, tableName);

            String postData = "{\"get\":\"" + permissions.getGetPermission()
                    + "\",\"put\":\"" + permissions.getPutPermission()
                    + "\",\"post\":\"" + permissions.getPostPermission()
                    + "\",\"patch\":\"" + permissions.getPatchPermission()
                    + "\",\"delete\":\"" + permissions.getDeletePermission()
                    + "\"}";

            AzureRestAPIHelper.putRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (Exception e) {
            throw new AzureCmdException("Error updating API", e);
        }
    }

    @Override
    public List<Job> listJobs(UUID subscriptionId, String serviceName) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/scheduler/jobs", subscriptionId.toString(), serviceName);

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Type type = new TypeToken<ArrayList<JobData>>() {
            }.getType();
            List<JobData> tempRes = new Gson().fromJson(json, type);

            List<Job> res = new ArrayList<Job>();

            for (JobData item : tempRes) {
                Job j = new Job();
                j.setAppName(item.getAppName());
                j.setName(item.getName());
                j.setEnabled(item.getStatus().equals("enabled"));
                j.setId(UUID.fromString(item.getId()));

                if (item.getIntervalPeriod() > 0) {
                    j.setIntervalPeriod(item.getIntervalPeriod());
                    j.setIntervalUnit(item.getIntervalUnit());
                }

                res.add(j);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting job list", e);
        }
    }

    @Override
    public void createJob(UUID subscriptionId, String serviceName, String jobName, int interval, String intervalUnit, String startDate) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/scheduler/jobs", subscriptionId.toString(), serviceName);

            String postData = "{\"name\":\"" + jobName + "\""
                    + (
                    intervalUnit.equals("none") ? "" : (",\"intervalUnit\":\"" + intervalUnit
                            + "\",\"intervalPeriod\":" + String.valueOf(interval)
                            + ",\"startTime\":\"" + startDate + "\""))
                    + "}";


            AzureRestAPIHelper.postRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (AzureCmdException e) {
            throw e;
        } catch (Exception e) {
            throw new AzureCmdException("Error creating jobs", e);
        }
    }

    @Override
    public void updateJob(UUID subscriptionId, String serviceName, String jobName, int interval, String intervalUnit, String startDate, boolean enabled) throws AzureCmdException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/scheduler/jobs/%s", subscriptionId.toString(), serviceName, jobName);

            String postData = "{"
                    + "\"status\":\"" + (enabled ? "enabled" : "disabled") + "\""
                    + (
                    intervalUnit.equals("none") ? "" : (",\"intervalUnit\":\"" + intervalUnit
                            + "\",\"intervalPeriod\":" + String.valueOf(interval)
                            + ",\"startTime\":\"" + startDate + "\""))
                    + "}";

            if (intervalUnit.equals("none")) {
                postData = "{\"status\":\"disabled\"}";
            }

            AzureRestAPIHelper.putRestApiCommand(path, postData, subscriptionId.toString(), null, true);
        } catch (Exception e) {
            throw new AzureCmdException("Error updating job", e);
        }
    }

    @Override
    public void downloadJobScript(UUID subscriptionId, String serviceName, String scriptName, String downloadPath) throws AzureCmdException {
        try {
            String jobName = scriptName.split("\\.")[0];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/scheduler/jobs/%s/script", subscriptionId.toString(), serviceName, jobName);
            String script = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(downloadPath), "utf-8"));
            writer.write(script);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
            //On error, create script for template
        }
    }

    @Override
    public void uploadJobScript(UUID subscriptionId, String serviceName, String scriptName, String filePath) throws AzureCmdException {
        try {
            String jobName = scriptName.split("\\.")[0];

            String path = String.format("/%s/services/mobileservices/mobileservices/%s/scheduler/jobs/%s/script", subscriptionId.toString(), serviceName, jobName);

            AzureRestAPIHelper.uploadScript(path, filePath, subscriptionId.toString());

        } catch (Exception e) {
            throw new AzureCmdException("Error upload script", e);
        }
    }

    @Override
    public List<LogEntry> listLog(UUID subscriptionId, String serviceName, String runtime) throws AzureCmdException, ParseException {
        try {
            String path = String.format("/%s/services/mobileservices/mobileservices/%s/logs?$top=10", subscriptionId.toString(), serviceName);

            String json = AzureRestAPIHelper.getRestApiCommand(path, subscriptionId.toString());

            LogData tempRes = new Gson().fromJson(json, LogData.class);

            List<LogEntry> res = new ArrayList<LogEntry>();

            for (LogData.LogEntry item : tempRes.getResults()) {
                LogEntry logEntry = new LogEntry();

                logEntry.setMessage(item.getMessage());
                logEntry.setSource(item.getSource());
                logEntry.setType(item.getType());

                SimpleDateFormat ISO8601DATEFORMAT;

                if (MobileService.NODE_RUNTIME.equals(runtime)) {
                    ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
                } else {
                    ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
                }
                logEntry.setTimeCreated(ISO8601DATEFORMAT.parse(item.getTimeCreated()));

                res.add(logEntry);
            }

            return res;
        } catch (Exception e) {
            throw new AzureCmdException("Error getting log", e);
        }
    }
}