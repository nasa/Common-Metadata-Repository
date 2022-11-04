(ns cmr.ous.core
  (:require
   [cmr.ous.impl.v1 :as v1]
   [cmr.ous.impl.v2-1 :as v2-1]
   [cmr.ous.impl.v3 :as v3]
   [taoensso.timbre :as log]))

(defn get-opendap-urls
  [component api-version user-token dap-version sa-header raw-params]
  (log/trace "Got API version:" api-version)
  (case (keyword api-version)
    :v1 (v1/get-opendap-urls component user-token raw-params sa-header)
    :v2.1 (v2-1/get-opendap-urls component user-token raw-params sa-header)
    :v3 (v3/get-opendap-urls component user-token dap-version raw-params sa-header)
    ;; XXX I believe EDSC is using 2.1 by default now, so we can change the
    ;;     default option to be that ...
    (v1/get-opendap-urls component user-token raw-params sa-header)))
