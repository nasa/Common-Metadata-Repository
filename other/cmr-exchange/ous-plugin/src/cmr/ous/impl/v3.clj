(ns cmr.ous.impl.v3
  "Version 3 was introduced to support DAP4. The default format is changed from DAP2 to DAP4 in v3."
  (:require
   [cmr.ous.impl.v2-1 :as v2-1]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-opendap-urls
  [component user-token dap-version raw-params input-sa-header]
  (let [dap-version (or dap-version "4")]
    (if (some #{dap-version} ["2" "4"])
      (v2-1/get-opendap-urls component user-token dap-version raw-params input-sa-header)
      {:errors [(format "Parameter dap-version can only be either 2 or 4, but was %s." dap-version)]})))
