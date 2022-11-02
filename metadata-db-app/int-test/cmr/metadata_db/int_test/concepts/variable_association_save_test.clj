(ns cmr.metadata-db.int-test.concepts.variable-association-save-test
  "Contains integration tests for saving variable associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "PROV1" :small false}))

(defn- inject-metadata
  "Take a concept which has a :metadata field and inject some content into the
   metadata.
   Parameters:
   original: concept map containing :metadata
   metadata-additions: a name/value list sutible for use with apply"
  [original metadata-additions]
  (-> original
      :metadata
      (json/parse-string)
      (as-> xs (apply assoc xs metadata-additions))
      (json/generate-string)
      (as-> xs (assoc original :metadata xs))))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-variable-association-failure-test
  (testing "saving new variable associations on non system-level provider"
    (let [coll (concepts/create-and-save-concept :collection "REG_PROV" 1)
          variable (concepts/create-and-save-concept :variable "REG_PROV" 1 1 {:coll-concept-id (:concept-id coll)})
          variable-association (-> (concepts/create-concept :variable-association coll variable 2)
                                   (assoc :provider-id "REG_PROV"))
          {:keys [status errors]} (util/save-concept variable-association)]

      (is (= 422 status))
      (is (= [(str "Variable association could not be associated with provider [REG_PROV]. "
                   "Variable associations are system level entities.")]
             errors)))))

(deftest save-variable-association-success-test
  (testing "saving new variable association successfully"
    (let [header {transmit-config/token-header (transmit-config/echo-system-token)}
          coll (concepts/create-and-save-concept :collection "PROV1" 1)
          variable (concepts/create-and-save-concept :variable "PROV1" 1 1
                                                     {:coll-concept-id (:concept-id coll)})
          variable-association (-> (concepts/create-concept :variable-association coll variable 2)
                                   (assoc :provider-id "CMR"))
          results (util/save-concept variable-association 1 header)
          {:keys [status errors]} results]
      (is (= 201 status))
      (is (nil? errors))
      (is (= 1 (:revision-id results)))
      (is (some? (:concept-id results)))))

;; This metadata is to be added to a variable for testing '&' inside of data in
;; The hope of tricking the content=foo&data=bar parser
(def additional-variable-metadata
  ["Description" "Update Content, with URIs & ampersands"
   "MeasurementIdentifiers" {"MeasurementContextMedium" "ocean"
                             "MeasurementContextMediumURI"
                             "http://fake.gov/ontology/ENVO?content=var&data=test",
                             "MeasurementObject" "sea_surface_subskin"
                             "MeasurementObjectURI"
                             "https://gcmd.earthdata.nasa.gov/kms/concept/68a09c56-be36-4100-8757-3a6eec7dc251?a=1&b=2"}])

  (testing "saving new variable association, with payload"
    (let [header {transmit-config/token-header (transmit-config/echo-system-token)}
          coll (concepts/create-and-save-concept :collection "PROV1" 1)
          variable (-> (concepts/create-and-save-concept :variable "PROV1" 1 1
                                                     {:coll-concept-id (:concept-id coll)})
                       (inject-metadata additional-variable-metadata))
          variable-association (-> (concepts/create-concept :variable-association coll variable 2)
                                   (assoc :provider-id "CMR"))
          expected-payload {"XYZ" "XYZ" "allow-regridding" true}
          ;; pull out data we need latter
          metadata (read-string (:metadata variable-association))
          metadata-withpayload (str (assoc metadata :data expected-payload))
          var-concept-id (:variable-concept-id metadata)
          ;; put it back together
          variable-association (-> variable-association
                                   (assoc :metadata metadata-withpayload
                                          :variable-concept-id var-concept-id)
                                   (assoc-in [:extra-fields :variable-concept-id] var-concept-id))
          results (util/save-concept variable-association 1 header)
          {:keys [status errors]} results]
      (is (= 201 status) "HTTP Status is wrong, expected 201")
      (is (nil? errors) "Errors were returned, expected none")
      (is (= 1 (:revision-id results)) "Bad revision, expected one.")
      (is (some? (:concept-id results)) "Expected a value for concept id")

      ;; now pull the record back out and check the payload
      (let [stored-response (util/get-concept-by-id-and-revision
                             (:concept-id results)
                             (:revision-id results))
            stored-concept (:concept stored-response)
            stored-payload (-> stored-concept
                               :metadata
                               read-string
                               :data)]
        (is (= 200 (:status stored-response)) "HTTP Status is wrong, expected 200")
        (is (= expected-payload stored-payload) "Payload missmatch")))))
