package com.pagerduty.go.notification.datadog;


import com.google.gson.GsonBuilder;
import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Resolution;
import com.squareup.pagerduty.incidents.Trigger;
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
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

@Extension
public class GoNotificationPlugin implements GoPlugin {
    private static final Logger LOGGER = Logger.getLoggerFor(GoNotificationPlugin.class);

    private static final String CONF_FILENAME = "pagerduty-notify.conf";

    private static PagerDuty pagerDuty;
    private static String hostname;
    private static List<String> pipelinesToMonitor;
    private static List<String> statusesToAlertOn;

    private static Map<String, String> currentIncidentKeys = null;

    public GoNotificationPlugin() {
        Config defaultConfig = null;
        Config config = null;

        defaultConfig = ConfigFactory.load(getClass().getClassLoader());    // This loads the resources/reference.conf file

        String userHome = System.getProperty("user.home");
        File configFile = new File(userHome + File.separator + CONF_FILENAME);
        if (!configFile.exists()) {
            LOGGER.warn(String.format("The configuration file %s was not found, using defaults. The configuration file must be set up for the plugin to work.", configFile));
            config = defaultConfig;
        } else {
            config = ConfigFactory.parseFile(configFile).withFallback(defaultConfig);
        }

        String apiKey = config.getString("pagerduty.api_key");
        pipelinesToMonitor = config.getStringList("pagerduty.pipelines");
        statusesToAlertOn = config.getStringList("pagerduty.statuses_to_alert");

        pagerDuty = PagerDuty.create(apiKey);

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to discern hostname, using blank hostname");
            hostname = "";
        }
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

        List<String> messages = new ArrayList<>();

        try {
            response.put("status", "success");
            GoNotificationMessage message = new GsonBuilder().registerTypeAdapter(Date.class, new Iso8601DateAdapter()).create().fromJson(goPluginApiRequest.requestBody(), GoNotificationMessage.class);

            String pipelineStage = message.getPipelineName() + "-" + message.getStageName();

            // Create an incident when matching pipelines fail
            if (pipelinesToMonitor.contains(message.getPipelineName()) && statusesToAlertOn.contains(message.getStageState())) {
                URI goURI = new URI("https", hostname, "/go/pipelines/" + message.getPipelineName() + "/" + message.getPipelineCounter() + "/" + message.getStageName() + "/" + message.getStageCounter());
                String goUrl = goURI.toURL().toString();

                Trigger trigger = new Trigger.Builder(String.format("Failed Build: %s build %s on %s", message.fullyQualifiedJobName(), message.getStageState(), hostname))
                        .client("GoCD")
                        .clientUrl(goUrl)
                        .build();
                NotifyResult result = pagerDuty.notify(trigger);
                currentIncidentKeys.put(pipelineStage, result.incidentKey());
            }

            // If that pipeline + stage passes, clear the incident
            if (currentIncidentKeys.containsKey(pipelineStage) && "Passed".equals(message.getStageResult())) {
                Resolution resolution = new Resolution.Builder(pipelineStage)
                        .withDescription(String.format("%s build %s on %s", message.fullyQualifiedJobName(), message.getStageState(), hostname))
                        .build();
                pagerDuty.notify(resolution);
                currentIncidentKeys.remove(pipelineStage);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling status message", e);
            responseCode = 500;
            response.put("status", "failure");
            if (!(e.getMessage().isEmpty())){
                messages.add(e.getMessage());
            }
        }

        if (!(messages.isEmpty())) {
            response.put("messages", messages);
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
