(ns cmr.client.base.protocol
  "This namespace defines the protocols used by CMR base clients.

  It is not expected that application developers who want to use the CMR client
  will ever use this namespace directly. It is indended for use by the three
  CMR service API clients.")

(defprotocol CMRClientAPI
  (^:export get-url [this segment])
  (^:export get-token [this])
  (^:export get-token-header [this]))
