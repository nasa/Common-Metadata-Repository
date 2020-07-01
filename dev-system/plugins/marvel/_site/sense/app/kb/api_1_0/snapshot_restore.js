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

define([],function(){return function(e){function t(e,t){for(var n,o=t.iterForCurrentLoc(),s=o.getCurrentToken();s&&s.type.indexOf("url")<0;){if("variable"===s.type&&'"type"'===s.value){if(s=t.parser.nextNonEmptyToken(o),!s||"punctuation.colon"!==s.type)break;s=t.parser.nextNonEmptyToken(o),s&&"string"===s.type&&(n=s.value.replace(/"/g,""));break}s=t.parser.prevNonEmptyToken(o)}return n}e.addEndpointDescription("restore_snapshot",{methods:["POST"],patterns:["_snapshot/{id}/{id}/_restore"],url_params:{wait_for_completion:"__flag__"},data_autocomplete_rules:{indices:"*",ignore_unavailable:{__one_of:[!0,!1]},include_global_state:!1,rename_pattern:"index_(.+)",rename_replacement:"restored_index_$1"}}),e.addEndpointDescription("single_snapshot",{methods:["GET","DELETE"],patterns:["_snapshot/{id}/{id}"]}),e.addEndpointDescription("all_snapshots",{methods:["GET"],patterns:["_snapshot/{id}/_all"]}),e.addEndpointDescription("put_snapshot",{methods:["PUT"],patterns:["_snapshot/{id}/{id}"],url_params:{wait_for_completion:"__flag__"},data_autocomplete_rules:{indices:"*",ignore_unavailable:{__one_of:[!0,!1]},include_global_state:{__one_of:[!0,!1]},partial:{__one_of:[!0,!1]}}}),e.addEndpointDescription("_snapshot_status",{methods:["GET"],patterns:["_snapshot/_status","_snapshot/{id}/_status","_snapshot/{id}/{ids}/_status"]}),e.addEndpointDescription("put_repository",{methods:["PUT"],patterns:["_snapshot/{id}"],data_autocomplete_rules:{__scope_link:function(e,n){var o=t(e,n);return o?{settings:{__scope_link:function(e,n){var o={fs:{__template:{location:"path"},location:"path",compress:{__one_of:[!0,!1]},concurrent_streams:5,chunk_size:"10m",max_restore_bytes_per_sec:"20mb",max_snapshot_bytes_per_sec:"20mb"},url:{__template:{url:""},url:"",concurrent_streams:5},s3:{__template:{bucket:""},bucket:"",region:"",base_path:"",concurrent_streams:5,chunk_size:"10m",compress:{__one_of:[!0,!1]}},hdfs:{__template:{path:""},uri:"",path:"some/path",load_defaults:{__one_of:[!0,!1]},conf_location:"cfg.xml",concurrent_streams:5,compress:{__one_of:[!0,!1]},chunk_size:"10m"}},s=t(e,n);return s||(console.log("failed to resolve snapshot, defaulting to 'fs'"),s="fs"),o[s]}}}:{type:{__one_of:["fs","url","s3","hdfs"]}}}}})}});