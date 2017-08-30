# Summary

This is the Cloud Foundry Firehose nozzle for [Application Insights](https://docs.microsoft.com/en-us/azure/application-insights/app-insights-overview). It monitors [app level logs](https://docs.cloudfoundry.org/devguide/deploy-apps/streaming-logs.html) and container metrics from Firehose, and generates following telemetries to Application Insights.
* **HTTP Request**: RTR logs are parsed and sent as Request telemetries.
* **Trace**: Application logs except RTR logs are sent as Trace telemetries.
* **Metric**: Metric telemetries track the CPU, memory and disk usage of application containers
* **Event**: Event telemetries track the following app events: **App Started**, **App Stopped**, **App Deleted**, **App Crashed**, **Staging Complete**, **SSH Success**, **SSH End**.

# Prerequisites
### 1. Deploy a CF or PCF environment on Azure

* [Deploy Cloud Foundry on Azure](http://docs.cloudfoundry.org/deploying/azure/index.html)
* [Deploy Pivotal Cloud Foundry on Azure](https://docs.pivotal.io/pivotalcf/1-9/customizing/azure.html)

### 2. Install CLIs on your dev box

* [Install Cloud Foundry CLI](https://github.com/cloudfoundry/cli#downloads)
* [Install Cloud Foundry UAA Command Line Client](https://github.com/cloudfoundry/cf-uaac/blob/master/README.md)

### 3. Create one or more Application Insights resources in Azure
For different applications, a separate Application Insights resource should be used for each app.
* [Create Application Insights resource](https://docs.microsoft.com/en-us/azure/application-insights/app-insights-create-new-resource)
* [Separate resources of Application Insights](https://docs.microsoft.com/en-us/azure/application-insights/app-insights-separate-resources)

# Deploy - Push the Nozzle as an App to Cloud Foundry
### 1. Utilize the CF CLI to authenticate with your CF instance
```
cf login -a https://api.$CF_SYSTEM_DOMAIN -u admin --skip-ssl-validation
```

### 2. Create a UAA client and grant required privileges
```
uaac target https://uaa.$CF_SYSTEM_DOMAIN --skip-ssl-validation
uaac token client get admin
uaac client add $CLIENT_ID \
  --name $CLIENT_ID \
  --secret $CLIENT_SECRET \
  --scope openid,oauth.approvals,doppler.firehose \
  --authorized_grant_types client_credentials,refresh_token \
  --authorities doppler.firehose,cloud_controller.admin \
  --access_token_validity  31557600 \
  --refresh_token_validity 31557600
```

### 3. Download the latest code
```
git clone https://github.com/lizzha/application-insights-java-nozzle.git
cd application-insights-java-nozzle
```

### 4. Build
```
./mvnw clean package
```

### 5. Get the application IDs
Get the IDs of the application to collect telemetries
```
cf app $APP_NAME --guid
```

### 6. Set environment variables in [manifest.yml](./manifest.yml)
```
CLIENT_ID              : The UAA client ID
CLIENT_SECRET          : The secret of the UAA client
API_ADDR               : The api URL of the CF environment, format: api.$CF_SYSTEM_DOMAIN
SKIP_SSL_VALIDATION    : If true, allows insecure connections to the UAA and the Trafficcontroller
LOG_LEVEL              : Logging level of the nozzle, valid levels: TRACE, DEBUG, INFO, ERROR
TELEMETRY_FILTER       : Telemetry types to ignore. Comma separated list, valid types: HttpRequest, Metric, AppEvent, Trace
APPLICATION_CONFIG     : A list of APPLICATION_ID and INSTRUMENTATION_KEY
 - APPLICATION_ID      : The ID of the application to collect telemetries
 - INSTRUMENTATION_KEY : The instrumentation key of the Application Insights resource.
```

### 5. Push the app
```
cf push
```