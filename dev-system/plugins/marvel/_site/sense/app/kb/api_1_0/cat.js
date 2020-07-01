/**
 * ELASTICSEARCH CONFIDENTIAL
 * _____________________________
 *
 *  [2014] Elasticsearch Incorporated All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Elasticsearch Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Elasticsearch Incorporated
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Elasticsearch Incorporated.
 */

define(["_"],function(a){function t(t,c,_,n){var e={help:"__flag__",v:"__flag__",bytes:["b"]};a.each(_||[],function(t){if(a.isString(t))e[t]="__flag__";else{var c=Object.keys(t)[0];e[c]=t[c]}}),c.addEndpointDescription(t,{match:t,url_params:e,patterns:n||[t]})}return function(a){t("_cat/aliases",a),t("_cat/allocation",a,null,["_cat/allocation","_cat/allocation/{nodes}"]),t("_cat/count",a),t("_cat/health",a,[{ts:["false","true"]}]),t("_cat/indices",a,[{h:[]},"pri"],["_cat/indices","_cat/indices/{indices}"]),t("_cat/master",a),t("_cat/nodes",a),t("_cat/pending_tasks",a),t("_cat/recovery",a),t("_cat/thread_pool",a),t("_cat/shards",a),t("_cat/plugins",a),t("_cat/segments",a)}});