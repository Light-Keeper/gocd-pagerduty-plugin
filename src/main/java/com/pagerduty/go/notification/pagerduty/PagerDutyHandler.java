package com.pagerduty.go.notification.pagerduty;

import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Resolution;
import com.squareup.pagerduty.incidents.Trigger;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PagerDutyHandler {
    private static final Logger LOGGER = Logger.getLoggerFor(PagerDutyHandler.class);

    private static Map<String, String> pipelineApiKeys = new HashMap<>();
    private static List<String> statusesToAlertOn;
    private static Map<String, String> currentIncidentKeys = new HashMap<>();
    private static String hostname;

    public PagerDutyHandler(Config config) {

        // Load API key list pipeline_name=api_key
        Config apiKeyConfig = config.getConfig("pagerduty.pipeline_api_keys");
        for (Map.Entry<String, ConfigValue> entry : apiKeyConfig.entrySet()) {
            pipelineApiKeys.put(entry.getKey(), entry.getValue().unwrapped().toString());
        }

        statusesToAlertOn = config.getStringList("pagerduty.statuses_to_alert");

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Unable to discern hostname, using blank hostname");
            hostname = "";
        }
    }

    public void handle(GoNotificationMessage message) throws Exception {

        String pipelineStage = message.getPipelineName() + "-" + message.getStageName();
        PagerDuty pd;

        // Create an incident when matching pipelines fail
        if (pipelineApiKeys.containsKey(message.getPipelineName()) && statusesToAlertOn.contains(message.getStageState())) {
            // TODO: Should we create a new incident for each failure or assume that if we've already created one that we're good?
            //       If we create multiple incidents it will be more challenging to resolve them after a pass, obviously.

            // If we don't already have an open incident for this pipeline/stage
            if (!currentIncidentKeys.containsKey(pipelineStage)){
                pd = newPagerDuty(pipelineApiKeys.get(message.getPipelineName()));

                String goUrl = getGoCDURL(message);

                Trigger trigger = new Trigger.Builder(String.format("Failed Build: %s build %s on %s", message.fullyQualifiedJobName(), message.getStageState(), hostname))
                        .client("GoCD")
                        .clientUrl(goUrl)
                        .build();
                NotifyResult result = pd.notify(trigger);
                currentIncidentKeys.put(pipelineStage, result.incidentKey());
            }
        } else if (currentIncidentKeys.containsKey(pipelineStage) && "Passed".equals(message.getStageResult())) {
            // If that pipeline + stage passes, clear the incident
            pd = newPagerDuty(pipelineApiKeys.get(message.getPipelineName()));

            Resolution resolution = new Resolution.Builder(currentIncidentKeys.get(pipelineStage))
                    .withDescription(String.format("%s build %s on %s", message.fullyQualifiedJobName(), message.getStageState(), hostname))
                    .build();
            pd.notify(resolution);
            currentIncidentKeys.remove(pipelineStage);
        }
    }

    protected PagerDuty newPagerDuty(String apiKey) {
        return PagerDuty.create(apiKey);
    }

    private String getGoCDURL(GoNotificationMessage message) throws URISyntaxException, MalformedURLException {
        URI goURI = new URI("https", hostname, "/go/pipelines/" + message.getPipelineName() + "/" + message.getPipelineCounter() + "/" + message.getStageName() + "/" + message.getStageCounter(),"");
        return goURI.toURL().toString();
    }

    protected void clearCurrentIncidents() {
        currentIncidentKeys = new HashMap<>();
    }
}
