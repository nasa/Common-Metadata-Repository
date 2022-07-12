(ns cmr.common-app.services.search.parameter-validation-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common-app.services.search.parameter-validation :as pv]))

(deftest validate-all-map-values-test
  (are3 [validate-fn paths params expected]
    (is (= expected (pv/validate-all-map-values validate-fn paths params)))

    "platforms_h: missing index and nested keys returns and error string"
    pv/validate-map
    [:platforms_h]
    {:platforms_h "single string"}
    [{}
     ["Parameter [:platforms_h] must include an index and nested key, platforms_h[n][...]=value."]]

    "platforms_h: array passed in returns an error"
    pv/validate-map
    [:platforms_h]
    {:platforms_h ["single string"]}
    [{}
     ["Parameter [:platforms_h] must include an index and nested key, platforms_h[n][...]=value."]]

    "platforms_h: valid params return the param and no errors"
    pv/validate-map
    [:platforms_h]
    {:platforms_h {:0 {:basis "Space-Based Platforms"
                       :category "Earth Observation Satellites"}}}
    [{:platforms_h {:0 {:basis "Space-Based Platforms"
                        :category "Earth Observation Satellites"}}}
     []]))
