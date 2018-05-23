(ns cmr.opendap.app.routes.rest.core
  (:require
   [cmr.opendap.app.routes.rest.v1 :as v1]
   [cmr.opendap.app.routes.rest.v2 :as v2]
   [taoensso.timbre :as log]))

(defn all
  [httpd-component version]
  (case (keyword version)
    :v1 (v1/all httpd-component)
    (v2/all httpd-component)))
