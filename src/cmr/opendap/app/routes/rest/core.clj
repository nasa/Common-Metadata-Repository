(ns cmr.opendap.app.routes.rest.core
  (:require
   [cmr.opendap.app.routes.rest.v1 :as v1]
   [cmr.opendap.app.routes.rest.v2 :as v2]
   [cmr.opendap.app.routes.rest.v2-1 :as v2-1]
   [taoensso.timbre :as log]))

(defn all
  "REST API version summary:
  * v1 - the first implementation of the REST API
  * v2 - changes to the admin API (in particular, how caching is managed)
  * v2.1 - changes in the behaviour of how URLs are generated, with non-gridded
           granules having any spatial subsetting requests stripped.

  Note that the REST API version is not to be confused with the two different
  versions used for query parameters. In that case, v1 parameters refer to what
  what used in the original Node.js prototype (WCS-compatibility), and the v2
  parameters refer to what has been done in the Clojure implementation, providing
  CMR/EDSC-compatible parameters."
  [httpd-component version]
  (case (keyword version)
    :v1 (v1/all httpd-component)
    :v2 (v2/all httpd-component)
    :v2.1 (v2-1/all httpd-component)))
