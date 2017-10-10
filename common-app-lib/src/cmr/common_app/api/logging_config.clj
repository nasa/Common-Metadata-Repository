(ns cmr.common-app.api.logging-config
  "Defines the logging routes for applications. There are three main functions 1) To get the current
   CMR logging configuration, 2) Change the logging configuration and 3) To reset the logging
   configuration to the start of the application. To get the logging
   configuration use the GET method to change the logging configuration use the PUT method. For
   more information and examples on how to get or change logging please see
   cmr.common-app.services.logging. The caller must have system ingest management update permission
   to call any of the logging routes. To avoid a cyclic load dependency the defined function below
   is in this namespace instead of in the routes.clj file in the same directory."
  (:require
   [cmr.common-app.services.logging-config :as log]
   [compojure.core :refer [context GET PUT]]))

(def logging-routes
  "Creates common routes for changing the logging configuration. The caller must have
  system ingest management update permission to call any of the logging routes."
  (context "/log" []

    ;; update the logging configuration
    (PUT "/"
         {:keys [request-context headers body]}
         (log/merge-logging-configuration request-context headers body))

    ;; retrieve the logging configuration
    (GET "/"
         {:keys [request-context headers]}
         (log/get-logging-configuration request-context headers))

    ;; reset the logging configuration
    (GET "/reset"
         {:keys [request-context headers]}
         (log/reset-logging-configuration request-context headers))))
