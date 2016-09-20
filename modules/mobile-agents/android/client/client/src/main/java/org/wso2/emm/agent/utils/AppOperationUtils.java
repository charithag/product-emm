/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.emm.agent.utils;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.emm.agent.AndroidAgentException;
import org.wso2.emm.agent.R;
import org.wso2.emm.agent.api.ApplicationManager;
import org.wso2.emm.agent.beans.AppOperation;
import org.wso2.emm.agent.beans.Operation;
import org.wso2.emm.agent.services.operation.OperationManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to hold application install, update and remove related state lifecycle methods.
 */
public class AppOperationUtils {

    private static Type listType = new TypeToken<ArrayList<AppOperation>>() {
    }.getType();
    private static Gson appOperationsGson = new Gson();
    private static final Object LOCK = new Object();

    private AppOperationUtils() {
    }

    private static List<AppOperation> getAppOperations(Context context) {
        String pendingAppOperationsString = Preference.getString(context, Constants.PENDING_APPLICATION_OPERATIONS);
        if (pendingAppOperationsString == null) {
            pendingAppOperationsString = "[]";
        }
        return appOperationsGson.fromJson(pendingAppOperationsString, listType);
    }

    public static AppOperation getAppOperation(Context context, String url, String identifier) {
        List<AppOperation> appOperations = getAppOperations(context);
        for (AppOperation appOp : appOperations) {
            if ((identifier != null && identifier.equals(appOp.getAppIdentifier())) || (url != null && url.equals(appOp.getUrl()))) {
                return appOp;
            }
        }
        return null;
    }

    public static void addToPendingAppOperationsList(Context context, OperationManager operationManager, Operation operation) throws AndroidAgentException {
        synchronized (LOCK) {
            try {
                JSONObject appData = new JSONObject(operation.getPayLoad().toString());
                String url = appData.getString(context.getString(R.string.app_url));
                String appIdentifier = appData.getString(context.getString(R.string.app_identifier));
                List<AppOperation> appOperations = getAppOperations(context);
                AppOperation appOperation = getAppOperation(context, url, appIdentifier);
                if (appOperation == null) {
                    appOperation = new AppOperation();
                    appOperation.setOperationId(operation.getId());
                    appOperation.setOperationCode(operation.getCode());
                    appOperation.setUrl(url);
                    appOperation.setAppIdentifier(appIdentifier);
                    appOperations.add(appOperation);
                } else {
                    for (AppOperation appOp : appOperations){
                        if ((appIdentifier != null && appIdentifier.equals(appOp.getAppIdentifier())) || (url != null && url.equals(appOp.getUrl()))) {
                            appOp.setOperationId(operation.getId());
                            appOp.setOperationCode(operation.getCode());
                            appOp.setOperationStatus(null);
                            appOp.setLastStatus(null);
                            appOp.setOperationErrorMessage(null);
                        }
                    }
                }
                Preference.putString(context, Constants.PENDING_APPLICATION_OPERATIONS, appOperationsGson.toJson(appOperations));
            } catch (JSONException e) {
                operation.setStatus(context.getResources().getString(R.string.operation_value_error));
                operation.setOperationResponse("Error in parsing APPLICATION payload.");
                operationManager.getResultBuilder().build(operation);
                throw new AndroidAgentException("Invalid JSON format.", e);
            }
        }
    }

    public static void updateApplicationStatus(Context context, String url, String identifier, String status, String error) {
        synchronized (LOCK) {
            List<AppOperation> appOperations = getAppOperations(context);
            for (AppOperation appOp : appOperations) {
                if ((identifier != null && identifier.equals(appOp.getAppIdentifier())) || (url != null && url.equals(appOp.getUrl()))) {
                    appOp.setOperationStatus(status);
                    appOp.setOperationErrorMessage(error);
                }
            }
            Preference.putString(context, Constants.PENDING_APPLICATION_OPERATIONS, appOperationsGson.toJson(appOperations));
        }
    }

    public static List<Operation> getAppOperationsToNotify(Context context) {
        synchronized (LOCK) {
            List<AppOperation> appOperations = getAppOperations(context);
            List<AppOperation> appOperationsInProgress = new ArrayList<>();
            List<Operation> operationsToNotify = new ArrayList<>();
            Operation applicationOperation = new Operation();
            for (AppOperation appOp : appOperations) {
                if (appOp.getOperationStatus() != null) {
                    ApplicationManager appMgt = new ApplicationManager(context);
                    applicationOperation.setId(appOp.getOperationId());
                    applicationOperation.setCode(appOp.getOperationCode());
                    applicationOperation = appMgt.getApplicationInstallationStatus(
                            applicationOperation, appOp.getOperationStatus(), appOp.getOperationErrorMessage());
                    if (!appOp.getOperationStatus().equals(appOp.getLastStatus())) {
                        operationsToNotify.add(applicationOperation);
                        appOp.setLastStatus(appOp.getOperationStatus());
                    }
                    if (applicationOperation.getStatus().equals(context.getResources()
                            .getString(R.string.operation_value_progress))) {
                        appOperationsInProgress.add(appOp);
                    }
                }
            }
            Preference.putString(context, Constants.PENDING_APPLICATION_OPERATIONS, appOperationsGson.toJson(appOperationsInProgress));
            return operationsToNotify;
        }
    }
}
