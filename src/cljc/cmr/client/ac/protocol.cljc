(ns cmr.client.ac.protocol
  "This namespace defines the protocols used by CMR access-control client.")

(defprotocol CMRAccessControlAPI
  (^:export get-acls [this http-options] [this query-params http-options])
  (^:export get-groups [this http-options] [this query-params http-options])
  (^:export get-health [this] [this http-options])
  (^:export get-permissions
   [this http-options]
   [this query-params http-options]))
