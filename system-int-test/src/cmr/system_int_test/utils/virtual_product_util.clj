(ns cmr.system-int-test.utils.virtual-product-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.mime-types :as mt]))



(defn translate-granule-entries
  "Translate the virtual granule entries to the corresponding source entries in the input json"
  [json-str]
  (client/post (url/virtual-product-translate-granule-entries-url)
               {:throw-exceptions false
                :content-type mt/json
                :body json-str
                :connection-manager (s/conn-mgr)}))