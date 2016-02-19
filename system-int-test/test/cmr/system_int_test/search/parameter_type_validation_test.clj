(ns cmr.system-int-test.search.parameter-type-validation-test
  "Integration tests for validating parameter types.  Performed at the integration
  level due to the tendency for pre-validation code to make assumptions about the
  shape of parameters."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.search-util :as search]))

(defn- is-bad-request?
  "Returns true if the given response returns a 400 error with the given error strings"
  [response expected-errors]
  (is (= 400 (:status response)))
  (is (= (sort expected-errors) (sort (:errors response)))))

(defn- test-map-type
  "Runs tests that the given parameter works for searches on the given concept id using
  the valid example map and produces errors non-map values.  other-params is an optional
  map (or maps) of other parameters to send with the query."
  [concept-type name valid-example-map & other-params]
  (testing (str "querying with " name " as a single value returns an error")
    (let [response (search/find-refs concept-type (into {name "a"} other-params))]
      (is-bad-request? response
                       [(str "Parameter [" name "] must include a nested key, " name "[...]=value.")])))
  (testing (str "querying with " name " as a list returns an error")
    (let [response (search/find-refs concept-type (into {name ["a" "b"]} other-params))]
      (is-bad-request? response
                       [(str "Parameter [" name "] must include a nested key, " name "[...]=value.")])))
  (testing (str "querying with " name " as a map succeeds")
    (let [response (search/find-refs concept-type (into {name valid-example-map} other-params))]
      (is (nil? (:errors response))))))

(deftest parameter-type-validations
  (test-map-type :collection "options" {:entry-title {:pattern "true"}})
  (test-map-type :collection "options[entry_title]" {:pattern "true"})
  (test-map-type :collection "options[platform]" {:pattern "true"})
  (test-map-type :collection "options[instrument]" {:pattern "true"})
  (test-map-type :collection "options[sensor]" {:pattern "true"})
  (test-map-type :collection "options[project]" {:and "true"})
  (test-map-type :collection "options[attribute]" {:exclude_collection "true"})
  (test-map-type :granule "exclude" {:echo_granule_id "G1234-PROV1"})
  (test-map-type :collection "science_keywords" {"0" {:topic "some-topic"}})
  (let [other-params {"science_keywords[0][topic]" "other-topic"}]
    (test-map-type :collection "science_keywords[1]" {:topic "some-topic"} other-params))

  (testing "invalid type alongside other invalid parameters produces multiple errors"
    (let [response (search/find-refs :granule {:options "bad" :page_size "twenty"})]
      (is-bad-request? response ["Parameter [options] must include a nested key, options[...]=value."
                                 "page_size must be a number between 0 and 2000"])))

  (testing "invalid parameter setting for a valid parameter produces  an error"
    (let [response (search/find-refs :granule {:options {:provider "foo"}})]
      (is-bad-request? response ["Invalid settings foo for parameter :provider"])))

  (testing "invalid exclude parameter value"
    (let [response (search/find-concepts-with-param-string
                     :granule "exclude[echo_granule_id][][]=G1-PROV1")]
      (is-bad-request?
        response ["Invalid format for exclude parameter, must be in the format of exclude[name][]=value"])))

  (testing "multiple invalid types produce multiple errors"
    (let [response (search/find-refs :granule {:options "bad" :exclude "also-bad"})]
      (is-bad-request? response ["Parameter [exclude] must include a nested key, exclude[...]=value."
                                 "Parameter [options] must include a nested key, options[...]=value."]))))
