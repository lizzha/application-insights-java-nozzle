package com.microsoft.nozzle.applicationinsights.cache;

import com.microsoft.nozzle.applicationinsights.message.BaseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AppDataCache {

    private final CloudFoundryClient cfClient;

    private final Map<String, String> appIdToNameMap = new HashMap<>();
    private final Map<String, String> spaceIdToNameMap = new HashMap<>();
    private final Map<String, String> orgIdToNameMap = new HashMap<>();

    private final Map<String, String> appIdToSpaceIdMap = new HashMap<>();
    private final Map<String, String> spaceIdToOrgIdMap = new HashMap<>();

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing App Data Cache");

        getAllApps();
        getAllSpaces();
        getAllOrgs();
    }

    /**
     * Get all apps
     */
    private void getAllApps() {
        getAppsFromPage(1);
    }

    /**
     * Get all spaces
     */
    private void getAllSpaces() {
        getSpacesFromPage(1);
    }

    /**
     * Get all organizations
     */
    private void getAllOrgs() {
        getOrgsFromPage(1);
    }

    /**
     * Get all apps starting from the page
     *
     * @param page
     */
    private void getAppsFromPage(int page) {
        if (page <= 0) {
            return;
        }

        cfClient.applicationsV2()
                .list(ListApplicationsRequest.builder()
                        .page(page)
                        .resultsPerPage(100)
                        .build())
                .subscribe(response -> {
                            response.getResources().forEach(app -> {
                                appIdToNameMap.put(app.getMetadata().getId(), app.getEntity().getName());
                                appIdToSpaceIdMap.put(app.getMetadata().getId(), app.getEntity().getSpaceId());
                            });
                            if (page < response.getTotalPages()) {
                                getAppsFromPage(page + 1);
                            }
                        },
                        t -> log.error("Error listing applications of page {}", page, t));
    }

    /**
     * Get all spaces starting from the page
     *
     * @param page
     */
    private void getSpacesFromPage(int page) {
        if (page <= 0) {
            return;
        }

        cfClient.spaces()
                .list(ListSpacesRequest.builder()
                        .page(page)
                        .resultsPerPage(100)
                        .build())
                .subscribe(response -> {
                            response.getResources().forEach(space -> {
                                spaceIdToNameMap.put(space.getMetadata().getId(), space.getEntity().getName());
                                spaceIdToOrgIdMap.put(space.getMetadata().getId(), space.getEntity().getOrganizationId());
                            });
                            if (page < response.getTotalPages()) {
                                getSpacesFromPage(page + 1);
                            }
                        },
                        t -> log.error("Error listing spaces of page {}", page, t));
    }

    /**
     * Get all organizations starting from the page
     *
     * @param page
     */
    private void getOrgsFromPage(int page) {
        if (page <= 0) {
            return;
        }

        cfClient.organizations()
                .list(ListOrganizationsRequest.builder()
                        .page(page)
                        .resultsPerPage(100)
                        .build())
                .subscribe(response -> {
                            response.getResources().forEach(org -> orgIdToNameMap.put(org.getMetadata().getId(), org.getEntity().getName()));
                            if (page < response.getTotalPages()) {
                                getOrgsFromPage(page + 1);
                            }
                        },
                        t -> log.error("Error listing organizations of page {}", page, t));
    }

    public void getAppData(String applicationId, BaseMessage message) {
        if (applicationId == null || applicationId.isEmpty()) {
            return;
        }

        message.setApplicationId(applicationId);
        message.setApplicationName(appIdToNameMap.get(applicationId));
        String spaceId = appIdToSpaceIdMap.get(applicationId);
        message.setSpaceId(spaceId);
        message.setSpaceName(spaceIdToNameMap.get(spaceId));
        String orgId = spaceIdToOrgIdMap.get(spaceId);
        message.setOrganizationId(orgId);
        message.setOrganizationName(orgIdToNameMap.get(orgId));
    }
}
