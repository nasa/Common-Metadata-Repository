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

define([],function(){return function(e){e.addEndpointDescription("_get_settings",{patterns:["{indices}/_settings","_settings"]}),e.addEndpointDescription("_put_settings",{methods:["PUT"],patterns:["{indices}/_settings","_settings"],data_autocomplete_rules:{index:{refresh_interval:"1s",number_of_shards:5,number_of_replicas:1,"blocks.read_only":{__one_of:[!1,!0]},"blocks.read":{__one_of:[!0,!1]},"blocks.write":{__one_of:[!0,!1]},"blocks.metadata":{__one_of:[!0,!1]},term_index_interval:32,term_index_divisor:1,"translog.flush_threshold_ops":5e3,"translog.flush_threshold_size":"200mb","translog.flush_threshold_period":"30m","translog.disable_flush":{__one_of:[!0,!1]},"cache.filter.max_size":"2gb","cache.filter.expire":"2h","gateway.snapshot_interval":"10s",routing:{allocation:{include:{tag:""},exclude:{tag:""},require:{tag:""},total_shards_per_node:-1}},"recovery.initial_shards":{__one_of:["quorum","quorum-1","half","full","full-1"]},"ttl.disable_purge":{__one_of:[!0,!1]},analysis:{analyzer:{},tokenizer:{},filter:{},char_filter:{}},"index.cache.query.enable":{__one_of:[!0,!1]}}}})}});