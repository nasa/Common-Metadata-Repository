(ns cmr.ous.impl.v3-1
  "Version 3.1 was introduced to support DAP4. The default format is changed from DAP2 to DAP4 in v3.1"
  (:require
    [cmr.ous.impl.v2-1 :as v2-1]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-opendap-urls
  [component user-token dap-version raw-params]
  (v2-1/get-opendap-urls component user-token (or dap-version "4") raw-params))
