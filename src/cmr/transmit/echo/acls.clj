(ns cmr.transmit.echo.acls
  "Contains functions for retrieving ACLs from the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]))


(defn get-acls-by-type
  [context type]
  (let [[status acls body] (r/rest-get context "/acls" {:query-params {:object_identity_type type
                                                                       :reference false}})]
    (case status
      200 (mapv c/echo-acl->cmr-acl acls)
      (r/unexpected-status-error! status body))))




