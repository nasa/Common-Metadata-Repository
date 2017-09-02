(ns cmr.client.base.protocol)

(defprotocol CMRClientAPI
  (^:export get-url [this segment]))
