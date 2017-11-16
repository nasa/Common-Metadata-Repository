(ns cmr.metadata-db.api.route-helpers
  (:require
   [cmr.common.mime-types :as mt]))

(def json-header
  {"Content-Type" (mt/with-utf-8 mt/json)})
