(ns ^{:doc "provides index related utilities."}
  cmr-system-int-test.index-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cmr-system-int-test.url-helper :as url]))

(defn index-catalog
  "Index the whole catalog"
  []
  (client/post (url/index-catalog-url)
               {:headers {"Echo-Token" "EFF42B0CD69D2B5AE040007F01000BCF"}})
  (client/post (url/elastic-flush-url)))
