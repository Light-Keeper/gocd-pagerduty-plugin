package com.pagerduty.go.notification.pagerduty;


import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.util.*;

@Extension
public class GoNotificationPlugin implements GoPlugin {
    private static final Logger LOGGER = Logger.getLoggerFor(GoNotificationPlugin.class);
    private static final String CONF_FILENAME = "pagerduty-notify.conf";
    private static PagerDutyHandler pipeline;

    public GoNotificationPlugin() {
        Config defaultConfig;
        Config config;

        defaultConfig = ConfigFactory.load(getClass().getClassLoader());    // This loads the resources/reference.conf file

        String userHome = System.getProperty("user.home");
        File configFile = new File(userHome + File.separator + CONF_FILENAME);
        if (!configFile.exists()) {
            LOGGER.warn(String.format("The configuration file %s was not found, using defaults. The configuration file must be set up for the plugin to work.", configFile));
            config = defaultConfig;
        } else {
            config = ConfigFactory.parseFile(configFile).withFallback(defaultConfig);
        }

        pipeline = new PagerDutyHandler(config);
    }

    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
    }

    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        if (goPluginApiRequest.requestName().equals("notifications-interested-in")) {
            return handleNotificationInterest();
        } else if (goPluginApiRequest.requestName().equals("stage-status")) {
            return handleStageNotification(goPluginApiRequest);
        }
        return null;
    }

    private GoPluginApiResponse handleNotificationInterest() {
        Map<String, Object> response = new HashMap<>();
        response.put("notifications", Collections.singletonList("stage-status"));
        return renderJSON(200, response);
    }

    private GoPluginApiResponse handleStageNotification(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<>();
        int responseCode = 200;

        List<String> errorMessages = new ArrayList<>();

        try {
            response.put("status", "success");
            GoNotificationMessage message = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goPluginApiRequest.requestBody(), GoNotificationMessage.class);

            pipeline.handle(message);

        } catch (Exception e) {
            LOGGER.error("Error handling status message", e);
            responseCode = 500;
            response.put("status", "failure");
            if (!(e.getMessage().isEmpty())){
                errorMessages.add(e.getMessage());
            }
        }

        if (!(errorMessages.isEmpty())) {
            response.put("messages", errorMessages);
        }

        return renderJSON(responseCode, response);
    }

    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier("notification", Collections.singletonList("1.0"));
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}
