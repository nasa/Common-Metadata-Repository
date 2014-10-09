(ns cmr.transmit.echo.providers
  "Contains functions for retrieving providers from the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]))

(comment


  (get-provider-id-guid-map {:system user/system})

)


(defn get-provider-guid-id-map
  "Returns a map of the provider guids to their ids"
  [context]
  (let [[status providers body] (r/rest-get context "/providers")]
    (case status
      200
      (into {} (for [{:keys [provider]} providers]
                 [(:id provider) (:provider_id provider)]))
      (r/unexpected-status-error! status body))))
