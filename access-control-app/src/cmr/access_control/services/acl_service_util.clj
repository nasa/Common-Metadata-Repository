(ns cmr.access-control.services.acl-service-util
  "Contains common utility functions used in ACL service"
  (:require
    [clojure.edn :as edn]
    [clojure.set :as set]
    [cmr.access-control.data.acl-schema :as schema]
    [cmr.access-control.services.acl-service-messages :as acl-msg]
    [cmr.access-control.services.group-service :as groups]
    [cmr.common.concepts :as concepts]
    [cmr.common.date-time-parser :as dtp]
    [cmr.common.services.errors :as errors]
    [cmr.acl.core :as acl]
    [cmr.common.util :as util]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db :as mdb1]
    [cmr.transmit.metadata-db2 :as mdb]
    [cmr.umm.acl-matchers :as acl-matchers]))
