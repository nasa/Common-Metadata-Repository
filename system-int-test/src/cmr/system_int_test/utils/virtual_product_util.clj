(ns cmr.system-int-test.utils.virtual-product-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.mime-types :as mt]))



(defn keep-virtual
  "Filter the granules which are not virtual from the input json"
  [json-str]
  (client/post (url/virtual-product-keep-virtual-url)
               {:throw-exceptions false
                :content-type mt/json
                :body json-str
                :connection-manager (s/conn-mgr)}))