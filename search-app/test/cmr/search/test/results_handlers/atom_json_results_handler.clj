(ns cmr.search.test.results-handlers.atom-json-results-handler
  (:require 
    [clojure.test :refer :all]
    [cmr.search.results-handlers.atom-json-results-handler :as atom-json-results-handler]))

(deftest remove-nonhdf-links-test
  (is (= [{:type "application/x-hdfeos" :other "other"}]
         (atom-json-results-handler/remove-nonhdf-links [{:type "application/x-hdfeos" :other "other"}
                                                         {:type "other type" :other "other"}
                                                         {:other "other"}]))))
