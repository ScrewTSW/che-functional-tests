package com.redhat.che.start_workspace_reporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.model.*;
import com.redhat.che.start_workspace_reporter.util.HttpRequestWrapper;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReporterMain {

    private static final Logger LOG = Logger.getLogger(ReporterMain.class.getName());
    private static final Gson gson = new Gson();
    private static final JsonParser parser = new JsonParser();
    private static final AtomicInteger pvc_cycles_count = new AtomicInteger(0);
    private static final AtomicInteger eph_cycles_count = new AtomicInteger(0);
    private static final Long ONE_DAY_MILLIS = 86_400_000L;
    private static final Long TIMESTAMP_NOW = (System.currentTimeMillis())/1000;
    private static final Long TIMESTAMP_YESTERDAY = (System.currentTimeMillis() - ONE_DAY_MILLIS)/1000;
    private static final Long TIMESTAMP_DAY_BEFORE_YESTERDAY = (System.currentTimeMillis() - ONE_DAY_MILLIS - ONE_DAY_MILLIS)/1000;
    private static final int ZABBIX_HISTORY_GET_ALL_ENTRIES = 0;

    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a = "1367263";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b = "1367264";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2 = "1367262";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a = "1367261";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a = "1369751";

    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a = "1367273";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b = "1367274";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2 = "1367272";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a = "1367271";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a = "1369753";

    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a = "1362108";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b = "1362174";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_2 = "1060894";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a = "1060893";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a = "1369750";

    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a = "1362114";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b = "1362180";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2 = "1060914";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a = "1060913";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a = "1369752";
    private static final float MINMAX_INIT_VALUE = -1f;

    private static final String SLACK_SUCCESS_COLOR = "#2EB886";
    private static final String SLACK_UNSTABLE_COLOR = "#FFAA00";
    private static final String SLACK_BROKEN_COLOR = "#FF0000";

    public static void main(String[] args) {
        HttpRequestWrapper wrapper = new HttpRequestWrapper(System.getenv("ZABBIX_URL"));
        HttpRequestWrapperResponse response = null;
        InputStream versionRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("version_request.json");
        InputStream loginRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("login_request.json");
        InputStream getHistoryRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("get_history_request.json");
        InputStream slackPostIS = ReporterMain.class.getClassLoader().getResourceAsStream("slack_post_template.json");
        assert versionRequestIS != null;
        InputStreamReader versionRequestISReader = new InputStreamReader(versionRequestIS);
        assert loginRequestIS != null;
        InputStreamReader loginRequestISReader = new InputStreamReader(loginRequestIS);
        assert getHistoryRequestIS != null;
        InputStreamReader getHistoryRequestISReader = new InputStreamReader(getHistoryRequestIS);
        assert slackPostIS != null;
        InputStreamReader slackPostISReader = new InputStreamReader(slackPostIS);
        ZabbixLoginParams loginParams = new ZabbixLoginParams(System.getenv("ZABBIX_USERNAME"), System.getenv("ZABBIX_PASSWORD"));
        JSONRPCRequest versionRequest = gson.fromJson(versionRequestISReader, JSONRPCRequest.class);
        JSONRPCRequest loginRequest = gson.fromJson(loginRequestISReader, JSONRPCRequest.class);
        SlackPost slackPost = gson.fromJson(slackPostISReader, SlackPost.class);

        try {
            HttpResponse tmp = wrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), versionRequest.toString());
            response = new HttpRequestWrapperResponse(tmp);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
        } catch (IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
        }
        if (response != null) {
            if (responseSuccess(response)) {
                LOG.log(Level.INFO, "Zabbix heartbeat successful.");
            } else {
                return;
            }
        }

        String zabbixAuthToken = null;
        loginRequest.setParams(parser.parse(gson.toJson(loginParams)));
        try {
            HttpResponse tmp = wrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), loginRequest.toString());
            response = new HttpRequestWrapperResponse(tmp);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
        } catch (IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
        }
        if (response != null) {
            if (responseSuccess(response)) {
                LOG.log(Level.INFO, "Zabbix login successful.");
                try {
                    zabbixAuthToken = response.asJSONRPCResponse().getResult().getAsString();
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to get login response auth token:"+e.getLocalizedMessage());
                    return;
                }
            } else {
                return;
            }
        }

        JSONRPCRequest getHistoryRequest = gson.fromJson(getHistoryRequestISReader, JSONRPCRequest.class);
        getHistoryRequest.setAuth(zabbixAuthToken);
        ZabbixHistoryParams historyParams = new ZabbixHistoryParams();
        Set<String> zabbixHistoryParamsItemIDs = new HashSet<>();

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a);

        historyParams.setItemids(zabbixHistoryParamsItemIDs);
        historyParams.setLimit(ZABBIX_HISTORY_GET_ALL_ENTRIES);
        historyParams.setOutput((Set<String>)null);
        historyParams.setSortfield(ZabbixHistoryParams.SortField.CLOCK.toString());
        historyParams.setSortorder(ZabbixHistoryParams.SortOrder.ASC.toString());

        List<ZabbixHistoryMetricsEntry> zabbixHistoryResults = new ArrayList<>();

        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);

        AtomicReference<Float> zabbix_starter_us_east_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_min = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);

        pvc_cycles_count.set(0);
        eph_cycles_count.set(0);
        historyParams.setTime_from(TIMESTAMP_DAY_BEFORE_YESTERDAY);
        historyParams.setTime_till(TIMESTAMP_YESTERDAY);
        getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

        if (!grabHistoryDataFromZabbix(wrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

        calculateZabbixResults(zabbixHistoryResults,
                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_preview_2a_start_max,
                zabbix_starter_us_east_1a_start_min, zabbix_starter_us_east_1b_start_min, zabbix_starter_us_east_2_start_min, zabbix_starter_us_east_2a_start_min, zabbix_starter_us_east_preview_2a_start_min,
                zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg,
                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max,
                zabbix_starter_us_east_1a_stop_min, zabbix_starter_us_east_1b_stop_min, zabbix_starter_us_east_2_stop_min, zabbix_starter_us_east_2a_stop_min, zabbix_starter_us_east_preview_2a_stop_min,
                zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg,
                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max,
                zabbix_starter_us_east_eph_1a_start_min, zabbix_starter_us_east_eph_1b_start_min, zabbix_starter_us_east_eph_2_start_min, zabbix_starter_us_east_eph_2a_start_min, zabbix_starter_us_east_eph_preview_2a_start_min,
                zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg,
                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max,
                zabbix_starter_us_east_eph_1a_stop_min, zabbix_starter_us_east_eph_1b_stop_min, zabbix_starter_us_east_eph_2_stop_min, zabbix_starter_us_east_eph_2a_stop_min, zabbix_starter_us_east_eph_preview_2a_stop_min,
                zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_start_min, zabbix_starter_us_east_1a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_stop_min, zabbix_starter_us_east_1a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_start_min, zabbix_starter_us_east_eph_1a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_stop_min, zabbix_starter_us_east_eph_1a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_1b_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_start_min, zabbix_starter_us_east_1b_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_1b_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_1b_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_stop_min, zabbix_starter_us_east_1b_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_1b_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_1b_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_start_min, zabbix_starter_us_east_eph_1b_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_1b_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_1b_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_stop_min, zabbix_starter_us_east_eph_1b_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_start_min, zabbix_starter_us_east_2_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_stop_min, zabbix_starter_us_east_2_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_start_min, zabbix_starter_us_east_eph_2_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_stop_min, zabbix_starter_us_east_eph_2_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_start_min, zabbix_starter_us_east_2a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_stop_min, zabbix_starter_us_east_2a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_start_min, zabbix_starter_us_east_eph_2a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_stop_min, zabbix_starter_us_east_eph_2a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_start_max, zabbix_starter_us_east_preview_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_start_min, zabbix_starter_us_east_preview_2a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_stop_min, zabbix_starter_us_east_preview_2a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_start_min, zabbix_starter_us_east_eph_preview_2a_start_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_stop_min, zabbix_starter_us_east_eph_preview_2a_stop_min);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        zabbixHistoryResults = new ArrayList<>();
        pvc_cycles_count.set(0);
        eph_cycles_count.set(0);
        historyParams.setTime_from(TIMESTAMP_YESTERDAY);
        historyParams.setTime_till(TIMESTAMP_NOW);
        getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

        if (!grabHistoryDataFromZabbix(wrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

        calculateZabbixResults(zabbixHistoryResults,
                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_preview_2a_start_max,
                zabbix_starter_us_east_1a_start_min, zabbix_starter_us_east_1b_start_min, zabbix_starter_us_east_2_start_min, zabbix_starter_us_east_2a_start_min, zabbix_starter_us_east_preview_2a_start_min,
                zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg,
                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max,
                zabbix_starter_us_east_1a_stop_min, zabbix_starter_us_east_1b_stop_min, zabbix_starter_us_east_2_stop_min, zabbix_starter_us_east_2a_stop_min, zabbix_starter_us_east_preview_2a_stop_min,
                zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg,
                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max,
                zabbix_starter_us_east_eph_1a_start_min, zabbix_starter_us_east_eph_1b_start_min, zabbix_starter_us_east_eph_2_start_min, zabbix_starter_us_east_eph_2a_start_min, zabbix_starter_us_east_eph_preview_2a_start_min,
                zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg,
                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max,
                zabbix_starter_us_east_eph_1a_stop_min, zabbix_starter_us_east_eph_1b_stop_min, zabbix_starter_us_east_eph_2_stop_min, zabbix_starter_us_east_eph_2a_stop_min, zabbix_starter_us_east_eph_preview_2a_stop_min,
                zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        LOG.info("Yesterday worksapce startup ephemeral 1a: max:" + yesterday_zabbix_starter_us_east_eph_1a_start_max.get() +
                " min:" + yesterday_zabbix_starter_us_east_eph_1a_start_min.get() +
                " avg:" + yesterday_zabbix_starter_us_east_eph_1a_start_avg.get());

        LOG.info("Worksapce startup ephemeral 1a: max:" + zabbix_starter_us_east_eph_1a_start_max.get() +
                " min:" + zabbix_starter_us_east_eph_1a_start_min.get() +
                " avg:" + zabbix_starter_us_east_eph_1a_start_avg.get() +
                " diff:" + String.format("%.2f",getPercentageDifference(yesterday_zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_max)) + "%");

        List<SlackPostAttachment> attachments = slackPost.getAttachments();
        List<SlackPostAttachment> newAttachments = new ArrayList<>();
        for (SlackPostAttachment a : attachments) {
            String attachmentColor = a.getColor();
            // If it's a field that needs to have it's values set
            if (attachmentColor != null) {
                LOG.info("Found attachment with special values:"+attachmentColor);
                switch(attachmentColor) {
                    case "STARTER_US_EAST_1A_START_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1a_start_max, yesterday_zabbix_starter_us_east_1a_start_min, yesterday_zabbix_starter_us_east_1a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_1a_start_max, yesterday_zabbix_starter_us_east_eph_1a_start_min, yesterday_zabbix_starter_us_east_eph_1a_start_avg,
                                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_min, zabbix_starter_us_east_1a_start_avg,
                                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_min, zabbix_starter_us_east_eph_1a_start_avg, a);
                        break;
                    case "STARTER_US_EAST_1A_STOP_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1a_stop_max, yesterday_zabbix_starter_us_east_1a_stop_min, yesterday_zabbix_starter_us_east_1a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_1a_stop_max, yesterday_zabbix_starter_us_east_eph_1a_stop_min, yesterday_zabbix_starter_us_east_eph_1a_stop_avg,
                                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1a_stop_min, zabbix_starter_us_east_1a_stop_avg,
                                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1a_stop_min, zabbix_starter_us_east_eph_1a_stop_avg, a);
                        break;
                    case "STARTER_US_EAST_1B_START_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1b_start_max, yesterday_zabbix_starter_us_east_1b_start_min, yesterday_zabbix_starter_us_east_1b_start_avg,
                                yesterday_zabbix_starter_us_east_eph_1b_start_max, yesterday_zabbix_starter_us_east_eph_1b_start_min, yesterday_zabbix_starter_us_east_eph_1b_start_avg,
                                zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_1b_start_min, zabbix_starter_us_east_1b_start_avg,
                                zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_1b_start_min, zabbix_starter_us_east_eph_1b_start_avg, a);
                        break;
                    case "STARTER_US_EAST_1B_STOP_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1b_stop_max, yesterday_zabbix_starter_us_east_1b_stop_min, yesterday_zabbix_starter_us_east_1b_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_1b_stop_max, yesterday_zabbix_starter_us_east_eph_1b_stop_min, yesterday_zabbix_starter_us_east_eph_1b_stop_avg,
                                zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_1b_stop_min, zabbix_starter_us_east_1b_stop_avg,
                                zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_1b_stop_min, zabbix_starter_us_east_eph_1b_stop_avg, a);
                        break;
                    case "STARTER_US_EAST_2_START_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2_start_max, yesterday_zabbix_starter_us_east_2_start_min, yesterday_zabbix_starter_us_east_2_start_avg,
                                yesterday_zabbix_starter_us_east_eph_2_start_max, yesterday_zabbix_starter_us_east_eph_2_start_min, yesterday_zabbix_starter_us_east_eph_2_start_avg,
                                zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2_start_min, zabbix_starter_us_east_2_start_avg,
                                zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2_start_min, zabbix_starter_us_east_eph_2_start_avg, a);
                        break;
                    case "STARTER_US_EAST_2_STOP_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2_stop_max, yesterday_zabbix_starter_us_east_2_stop_min, yesterday_zabbix_starter_us_east_2_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_2_stop_max, yesterday_zabbix_starter_us_east_eph_2_stop_min, yesterday_zabbix_starter_us_east_eph_2_stop_avg,
                                zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2_stop_min, zabbix_starter_us_east_2_stop_avg,
                                zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2_stop_min, zabbix_starter_us_east_eph_2_stop_avg, a);
                        break;
                    case "STARTER_US_EAST_2A_START_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2a_start_max, yesterday_zabbix_starter_us_east_2a_start_min, yesterday_zabbix_starter_us_east_2a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_2a_start_max, yesterday_zabbix_starter_us_east_eph_2a_start_min, yesterday_zabbix_starter_us_east_eph_2a_start_avg,
                                zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_2a_start_min, zabbix_starter_us_east_2a_start_avg,
                                zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_2a_start_min, zabbix_starter_us_east_eph_2a_start_avg, a);
                        break;
                    case "STARTER_US_EAST_2A_STOP_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2a_stop_max, yesterday_zabbix_starter_us_east_2a_stop_min, yesterday_zabbix_starter_us_east_2a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_2a_stop_max, yesterday_zabbix_starter_us_east_eph_2a_stop_min, yesterday_zabbix_starter_us_east_eph_2a_stop_avg,
                                zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_2a_stop_min, zabbix_starter_us_east_2a_stop_avg,
                                zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_2a_stop_min, zabbix_starter_us_east_eph_2a_stop_avg, a);
                        break;
                    case "STARTER_US_EAST_2A_PREVIEW_START_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_preview_2a_start_max, yesterday_zabbix_starter_us_east_preview_2a_start_min, yesterday_zabbix_starter_us_east_preview_2a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_preview_2a_start_max, yesterday_zabbix_starter_us_east_eph_preview_2a_start_min, yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg,
                                zabbix_starter_us_east_preview_2a_start_max, zabbix_starter_us_east_preview_2a_start_min, zabbix_starter_us_east_preview_2a_start_avg,
                                zabbix_starter_us_east_eph_preview_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_min, zabbix_starter_us_east_eph_preview_2a_start_avg, a);
                        break;
                    case "STARTER_US_EAST_2A_PREVIEW_STOP_COLOR":
                        getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_preview_2a_stop_max, yesterday_zabbix_starter_us_east_preview_2a_stop_min, yesterday_zabbix_starter_us_east_preview_2a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max, yesterday_zabbix_starter_us_east_eph_preview_2a_stop_min, yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg,
                                zabbix_starter_us_east_preview_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_min, zabbix_starter_us_east_preview_2a_stop_avg,
                                zabbix_starter_us_east_eph_preview_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_min, zabbix_starter_us_east_eph_preview_2a_stop_avg, a);
                        break;
                    default: break;
                }
            }
            newAttachments.add(a);
        }
        slackPost.setAttachments(newAttachments);
        LOG.info(gson.toJson(slackPost));
    }

    private static void getMaxChangeAndSetColor(AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_max, AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_min, AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_avg, AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_max, AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_min, AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_1a_start_min, AtomicReference<Float> zabbix_starter_us_east_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg, SlackPostAttachment a) {
        float start_1a_maxChange = getMaxChange(
                yesterday_zabbix_starter_us_east_1a_start_max, yesterday_zabbix_starter_us_east_1a_start_min, yesterday_zabbix_starter_us_east_1a_start_avg,
                yesterday_zabbix_starter_us_east_eph_1a_start_max, yesterday_zabbix_starter_us_east_eph_1a_start_min, yesterday_zabbix_starter_us_east_eph_1a_start_avg,
                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_min, zabbix_starter_us_east_1a_start_avg,
                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_min, zabbix_starter_us_east_eph_1a_start_avg);
        a.setColor(getColorBasedOnPercentage(start_1a_maxChange));
    }

    private static float getMaxChange(AtomicReference<Float> oldPvcMax, AtomicReference<Float> oldPvcMin, AtomicReference<Float> oldPvcAvg, AtomicReference<Float> oldEphMax, AtomicReference<Float> oldEphMin, AtomicReference<Float> oldEphAvg, AtomicReference<Float> newPvcMax, AtomicReference<Float> newPvcMin, AtomicReference<Float> newPvcAvg, AtomicReference<Float> newEphMax, AtomicReference<Float> newEphMin, AtomicReference<Float> newEphAvg) {
        float pvc_avg_change = getPercentageDifference(oldPvcAvg, newPvcAvg);
        float pvc_max_change = getPercentageDifference(oldPvcMax, newPvcMax);
        float pvc_min_change = getPercentageDifference(oldPvcMin, newPvcMin);
        float eph_avg_change = getPercentageDifference(oldEphAvg, newEphAvg);
        float eph_max_change = getPercentageDifference(oldEphMax, newEphMax);
        float eph_min_change = getPercentageDifference(oldEphMin, newEphMin);
        return Math.max(eph_avg_change, Math.max(eph_max_change, Math.max(eph_min_change, Math.max(pvc_avg_change, Math.max(pvc_max_change, pvc_min_change)))));
    }

    private static void storeZabbixValueForComparison(AtomicReference<Float> storeValue, AtomicReference<Float> currentValue) {
        storeValue.set(currentValue.get());
        currentValue.set(MINMAX_INIT_VALUE);
    }

    private static float getPercentageDifference(AtomicReference<Float> oldValue, AtomicReference<Float> newValue) {
        return (newValue.get() - oldValue.get()) / oldValue.get() * 100;
    }

    private static String getColorBasedOnPercentage(float percentage) {
        if (percentage > 5f) return SLACK_BROKEN_COLOR;
        if (percentage > 0f) return SLACK_UNSTABLE_COLOR;
        return SLACK_SUCCESS_COLOR;
    }

    private static boolean grabHistoryDataFromZabbix(HttpRequestWrapper wrapper, HttpRequestWrapperResponse response, JSONRPCRequest getHistoryRequest, List<ZabbixHistoryMetricsEntry> zabbixHistoryResults) {
        LOG.info("Zabbix get history request:\n"+getHistoryRequest.toString());
        try {
            HttpResponse tmp = wrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), getHistoryRequest.toString());
            response = new HttpRequestWrapperResponse(tmp);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
        } catch (IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
        }
        if (response != null) {
            if (responseSuccess(response)) {
                LOG.log(Level.INFO, "Zabbix getHistory request successful.");
                try {
                    JsonArray tmp = gson.fromJson(response.asJSONRPCResponse().getResult(), JsonArray.class);
                    tmp.forEach(e -> zabbixHistoryResults.add(gson.fromJson(e, ZabbixHistoryMetricsEntry.class)));
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to parse response into value list:"+e.getLocalizedMessage());
                    return false;
                }
            } else {
                LOG.log(Level.SEVERE, "Zabbix getHistory request failed.");
                return false;
            }
        }
        return true;
    }

    private static void calculateZabbixResults(List<ZabbixHistoryMetricsEntry> zabbixHistoryResults, AtomicReference<Float> zabbix_starter_us_east_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_1b_start_max, AtomicReference<Float> zabbix_starter_us_east_2_start_max, AtomicReference<Float> zabbix_starter_us_east_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_1a_start_min, AtomicReference<Float> zabbix_starter_us_east_1b_start_min, AtomicReference<Float> zabbix_starter_us_east_2_start_min, AtomicReference<Float> zabbix_starter_us_east_2a_start_min, AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_min, AtomicReference<Float> zabbix_starter_us_east_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_1b_start_avg, AtomicReference<Float> zabbix_starter_us_east_2_start_avg, AtomicReference<Float> zabbix_starter_us_east_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_1b_stop_max, AtomicReference<Float> zabbix_starter_us_east_2_stop_max, AtomicReference<Float> zabbix_starter_us_east_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_1a_stop_min, AtomicReference<Float> zabbix_starter_us_east_1b_stop_min, AtomicReference<Float> zabbix_starter_us_east_2_stop_min, AtomicReference<Float> zabbix_starter_us_east_2a_stop_min, AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_min, AtomicReference<Float> zabbix_starter_us_east_1a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_1b_stop_avg, AtomicReference<Float> zabbix_starter_us_east_2_stop_avg, AtomicReference<Float> zabbix_starter_us_east_2a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_2_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_2_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_min, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_min, AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_min, AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_min, AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_min, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_min, AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_avg) {
        for (ZabbixHistoryMetricsEntry e : zabbixHistoryResults) {
            switch (e.getItemid()) {
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a:
                    eph_cycles_count.set(eph_cycles_count.get() + 1);
                    updateValues(zabbix_starter_us_east_eph_1a_start_min,
                            zabbix_starter_us_east_eph_1a_start_max,
                            zabbix_starter_us_east_eph_1a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_eph_1b_start_min,
                            zabbix_starter_us_east_eph_1b_start_max,
                            zabbix_starter_us_east_eph_1b_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_eph_2_start_min,
                            zabbix_starter_us_east_eph_2_start_max,
                            zabbix_starter_us_east_eph_2_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_eph_2a_start_min,
                            zabbix_starter_us_east_eph_2a_start_max,
                            zabbix_starter_us_east_eph_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_eph_preview_2a_start_min,
                            zabbix_starter_us_east_eph_preview_2a_start_max,
                            zabbix_starter_us_east_eph_preview_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a:
                    updateValues(zabbix_starter_us_east_eph_1a_stop_min,
                            zabbix_starter_us_east_eph_1a_stop_max,
                            zabbix_starter_us_east_eph_1a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_eph_1b_stop_min,
                            zabbix_starter_us_east_eph_1b_stop_max,
                            zabbix_starter_us_east_eph_1b_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_eph_2_stop_min,
                            zabbix_starter_us_east_eph_2_stop_max,
                            zabbix_starter_us_east_eph_2_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_eph_2a_stop_min,
                            zabbix_starter_us_east_eph_2a_stop_max,
                            zabbix_starter_us_east_eph_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_eph_preview_2a_stop_min,
                            zabbix_starter_us_east_eph_preview_2a_stop_max,
                            zabbix_starter_us_east_eph_preview_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a:
                    pvc_cycles_count.set(pvc_cycles_count.get() + 1);
                    updateValues(zabbix_starter_us_east_1a_start_min,
                            zabbix_starter_us_east_1a_start_max,
                            zabbix_starter_us_east_1a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_1b_start_min,
                            zabbix_starter_us_east_1b_start_max,
                            zabbix_starter_us_east_1b_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_2_start_min,
                            zabbix_starter_us_east_2_start_max,
                            zabbix_starter_us_east_2_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_2a_start_min,
                            zabbix_starter_us_east_2a_start_max,
                            zabbix_starter_us_east_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_preview_2a_start_min,
                            zabbix_starter_us_east_preview_2a_start_max,
                            zabbix_starter_us_east_preview_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a:
                    updateValues(zabbix_starter_us_east_1a_stop_min,
                            zabbix_starter_us_east_1a_stop_max,
                            zabbix_starter_us_east_1a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_1b_stop_min,
                            zabbix_starter_us_east_1b_stop_max,
                            zabbix_starter_us_east_1b_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_2_stop_min,
                            zabbix_starter_us_east_2_stop_max,
                            zabbix_starter_us_east_2_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_2a_stop_min,
                            zabbix_starter_us_east_2a_stop_max,
                            zabbix_starter_us_east_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_preview_2a_stop_min,
                            zabbix_starter_us_east_preview_2a_stop_max,
                            zabbix_starter_us_east_preview_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                default: break;
            }
        }
        if (zabbix_starter_us_east_1a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1a_start_avg.set(zabbix_starter_us_east_1a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1b_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1b_start_avg.set(zabbix_starter_us_east_1b_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2_start_avg.set(zabbix_starter_us_east_2_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2a_start_avg.set(zabbix_starter_us_east_2a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_preview_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_preview_2a_start_avg.set(zabbix_starter_us_east_preview_2a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1a_stop_avg.set(zabbix_starter_us_east_1a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1b_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1b_stop_avg.set(zabbix_starter_us_east_1b_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2_stop_avg.set(zabbix_starter_us_east_2_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2a_stop_avg.set(zabbix_starter_us_east_2a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_preview_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_preview_2a_stop_avg.set(zabbix_starter_us_east_preview_2a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_eph_1a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1a_start_avg.set(zabbix_starter_us_east_eph_1a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1b_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1b_start_avg.set(zabbix_starter_us_east_eph_1b_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2_start_avg.set(zabbix_starter_us_east_eph_2_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2a_start_avg.set(zabbix_starter_us_east_eph_2a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_preview_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_preview_2a_start_avg.set(zabbix_starter_us_east_eph_preview_2a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1a_stop_avg.set(zabbix_starter_us_east_eph_1a_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1b_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1b_stop_avg.set(zabbix_starter_us_east_eph_1b_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2_stop_avg.set(zabbix_starter_us_east_eph_2_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2a_stop_avg.set(zabbix_starter_us_east_eph_2a_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_preview_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_preview_2a_stop_avg.set(zabbix_starter_us_east_eph_preview_2a_stop_avg.get() / eph_cycles_count.get());
    }

    private static boolean responseSuccess(HttpRequestWrapperResponse response) {
        int responseStatusCode = response.getStatusCode();
        if (responseStatusCode != 200) return false;
        try {
            String responseString = response.grabContent();
            LOG.log(Level.INFO, "Status:" + responseStatusCode + "\n"
                                    + "Response:" + responseString);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to get response content:" + e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    private static void updateValues(AtomicReference<Float> min, AtomicReference<Float> max, AtomicReference<Float> avg, float value) {
        if (min.get() == MINMAX_INIT_VALUE) {
            min.set(value);
        } else {
            if (min.get() > value) {
                min.set(value);
            }
        }
        if (max.get() == MINMAX_INIT_VALUE) {
            max.set(value);
        } else {
            if (max.get() < value) {
                max.set(value);
            }
        }
        if (avg.get() == MINMAX_INIT_VALUE) {
            avg.set(value);
        } else {
            avg.set(avg.get() + value);
        }
    }
}
