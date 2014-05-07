(ns ^{:doc "provides index related utilities."}
  cmr.system-int-test.utils.index-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn flush-elastic-index
  []
  (client/post (url/elastic-flush-url)))

