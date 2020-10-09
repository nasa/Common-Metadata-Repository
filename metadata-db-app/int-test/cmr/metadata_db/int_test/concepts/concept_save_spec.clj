(ns cmr.metadata-db.int-test.concepts.concept-save-spec
  "Defines a common set of tests for saving concepts."
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.concepts :as cc]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.concept-validations :as v]))

;; Need this instead of direct string comparisons because we don't know ahead of time what
;; concept-ids will be generated, so the error messages must be checked with regexes instead
;; of exact strings.
(defn- assert-error-messages-match
  "Compare the returned error messages to the expected ones given as a vector of regexes."
  [exp-errors errors]
  (let [unexpected-errors (for [error errors
                                :when (not (some #(re-matches % error) exp-errors))]
                            error)
        missing-errors (for [error-regex exp-errors
                             :when (not (some #(re-matches error-regex %) errors))]
                         error-regex)]
    (is (empty? unexpected-errors) "The errors were not expected")
    (is (empty? missing-errors) "Some expected errors were missing")))

(defn- wrong-concept-prefix
  "For the given concept type return a prefix that is guarnteed not to match."
  [concept-type]
  (let [different-concept-type (first (disj cc/concept-types concept-type))]
    (cc/concept-type->concept-prefix different-concept-type)))

(defn save-concept-test
  "Attempt to save a concept and compare the result to the expected result."
  ([concept exp-status exp-revision-id exp-errors]
   (save-concept-test concept exp-status exp-revision-id exp-errors 1))
  ([concept exp-status exp-revision-id exp-errors exp-va-revision-id]
   (testing "save concept"
     (let [{:keys [status revision-id concept-id errors variable-association]} (util/save-concept concept)]
       (is (= exp-status status))
       (is (= exp-revision-id revision-id))
       (assert-error-messages-match exp-errors errors)
       (when (= 201 exp-status)
         (util/verify-concept-was-saved
           (assoc concept :revision-id revision-id :concept-id concept-id)))

       (when (and (= :variable (:concept-type concept))
                  (nil? (seq errors)))
         (is (= exp-va-revision-id (:revision-id variable-association))))
       ;; return `true` so we can use this inside an `are` macro
       true))))

(defn save-concept-with-revision-id-test
  "Save a concept once then save it again with the provided revision id, validating that the
  response matches the expected response, and possibly that the second save succeeded."
  [concept exp-status new-revision-id exp-errors]
  (time-keeper/clear-current-time!)
  (testing "save concept with revision id"
    (let [{:keys [concept-id revision-id variable-association]} (util/save-concept concept)
          revision-date1 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                 [:concept :revision-date])
          va1 variable-association
          va-revision-date1 (get-in (util/get-concept-by-id-and-revision
                                      (:concept-id va1) (:revision-id va1))
                                    [:concept :revision-date])
          updated-concept (assoc concept :revision-id new-revision-id :concept-id concept-id)
          {:keys [status errors revision-id variable-association]} (util/save-concept updated-concept)
          va2 variable-association
          va-revision-date2 (get-in (util/get-concept-by-id-and-revision
                                      (:concept-id va2) (:revision-id va2))
                                    [:concept :revision-date])
          revision-date2 (get-in (util/get-concept-by-id-and-revision concept-id revision-id)
                                 [:concept :revision-date])]
      (is (= exp-status status))
      (assert-error-messages-match exp-errors errors)
      (when (= 201 exp-status)
        (is (= new-revision-id revision-id))
        (is (t/after? revision-date2 revision-date1))
        (util/verify-concept-was-saved updated-concept))

      (when (and (= :variable (:concept-type concept))
                 (nil? (seq errors)))
        (is (t/after? va-revision-date2 va-revision-date1))
        (is (= 1 (:revision-id va1)))
        (is (= 2 (:revision-id va2)))))))

(defn save-concept-validate-field-saved-test
  "Save a concept and validate the the saved concept has the same value for the given field."
  [concept field]
  (testing "save concept with given field"
    (let [ {:keys [status revision-id concept-id]} (util/save-concept concept)]
      (is (= 201 status))
      (is (= revision-id 1))
      (let [retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)]
        (is (= (get concept field) (get (:concept retrieved-concept) field)))))))

(defn save-distinct-concepts-test
  "Save two different but similar concepts and verify that they get different concept-ids."
  [concept1 concept2]
  (testing "save different concepts"
    (let [{concept-id1 :concept-id revision-id1 :revision-id} (util/save-concept concept1)
          {concept-id2 :concept-id revision-id2 :revision-id} (util/save-concept concept2)]
      (util/verify-concept-was-saved (assoc concept1 :revision-id revision-id1 :concept-id concept-id1))
      (util/verify-concept-was-saved (assoc concept2 :revision-id revision-id2 :concept-id concept-id2))
      (is (not= concept-id1 concept-id2)))))

(defmulti gen-concept
  "Create a concept for a given type and provider-id."
  (fn [concept-type provider-id uniq-num attributes]
    concept-type))

(defmethod gen-concept :variable
  [_ provider-id uniq-num attributes]
  (let [coll (concepts/create-and-save-concept :collection provider-id uniq-num 1)
        coll-concept-id (:concept-id coll)
        attributes (merge {:coll-concept-id coll-concept-id} attributes)]
    (concepts/create-concept :variable provider-id uniq-num attributes)))

(def missing-parameter-errors
  "Map of parameters to expected error messages if the parameters are missing"
  {:concept-type [#"Concept must include concept-type."
                  #"Concept field \[concept-type\] cannot be nil."]
   :provider-id [#"Concept must include provider-id."]
   :native-id [#"Concept must include native-id."]
   :extra-fields [#"Concept must include extra-fields"]})

(defn save-test-with-missing-required-parameters
  "Test for missing parameters"
  [concept-type provider-ids req-params]
  (doseq [provider-id provider-ids
          [idx param] (map-indexed vector req-params)]
    (testing (format "save concept with missing required parameters %s fails" param)
      (let [concept (gen-concept concept-type provider-id idx {})
            field-errors (param missing-parameter-errors)]
        (save-concept-test (dissoc concept param) 422 nil field-errors)))))

(defn general-save-concept-test
  "Save tests that all concepts should pass"
  [concept-type provider-ids]
  (doseq [[idx provider-id] (map-indexed vector provider-ids)]
    (testing "basic save"
      (let [concept (gen-concept concept-type provider-id 1 {})]
        (save-concept-test concept 201 1 nil 1)
        (testing "save again with same concept-id"
          (save-concept-test concept 201 2 nil 2))))

    (testing "save with revision-date"
      (let [concept (gen-concept concept-type provider-id 2 {:revision-date (t/date-time 2001 1 1 12 12 14)})]
        (save-concept-validate-field-saved-test concept :revision-date)))

    (testing "save with bad revision-date"
      (let [concept (gen-concept concept-type provider-id 3 {:revision-date "foo"})]
        (save-concept-test concept 422 nil [#"\[foo\] is not a valid datetime"])))

    (testing "save with proper revision-id"
      (let [concept (gen-concept concept-type provider-id 5 {})]
        (save-concept-with-revision-id-test concept 201 2 nil)))

    (testing "save with skipped revisions"
      (let [concept (gen-concept concept-type provider-id 6 {})]
        (save-concept-with-revision-id-test concept 201 100 nil)))

    (testing "save with low revision fails"
      (let [concept (gen-concept concept-type provider-id 7 {})]
        (save-concept-with-revision-id-test
          concept 409 1 [#"Expected revision-id of \[2\] got \[1\] for \[\w+-\w+\]"])))

    (testing "save concept with revision-id 0 fails"
      (let [concept-with-bad-revision (gen-concept concept-type provider-id 18 {:revision-id 0})]
        (save-concept-test
          concept-with-bad-revision 409 nil [#"Expected revision-id of \[1\] got \[0\] for \[null\]"])))

    (testing "save concept with revision-id > MAX_REVISION_ID fails"
      (let [concept-with-bad-revision (gen-concept concept-type provider-id 18
                                                   {:revision-id (inc v/MAX_REVISION_ID)})]
        (save-concept-test
          concept-with-bad-revision 422 nil [#"revision-id \[\d+\] exceeds the maximum allowed value of \d+."])))

    (testing "save with concept-id"
      (let [prefix (cc/concept-type->concept-prefix concept-type)
            concept-id (str prefix "10000-" provider-id)
            concept (gen-concept concept-type provider-id 19 {:concept-id concept-id})]
        (save-concept-test concept 201 1 nil 1)

        (testing "get concept-id"
          (is (= {:status 200 :concept-id concept-id :errors nil}
                 (util/get-concept-id concept-type provider-id (:native-id concept)))))

        (testing "with incorrect concept-id prefix"
          (let [bad-id (str (wrong-concept-prefix concept-type) "12345-" provider-id)
                bad-concept (assoc concept :concept-id bad-id)]
            (save-concept-test bad-concept 422 nil [#"Concept-id \[.+\] for concept does not match provider-id \[.+\] or concept-type \[.+\]\."])))

        (testing "with incorrect native id"
          (let [concept-native (assoc concept :native-id "foo")]
            (save-concept-test concept-native 409 nil [#"A concept with concept-id \[\w+-\w+\] and native-id \[.*?\] already exists for concept-type \[:[\w-]+\] provider-id \[\w+\]. The given concept-id \[\w+-\w+\] and native-id \[foo\] would conflict with that one."])))

        (testing "with incorrect concept id"
          (let [other-concept-id (str (cc/concept-type->concept-prefix concept-type) "11-" provider-id)]
            (save-concept-test (assoc concept :concept-id other-concept-id)
                               409 nil [#"A concept with concept-id \[\w+-\w+\] and native-id \[.*?\] already exists for concept-type \[:[\w-]+\] provider-id \[\w+\]. The given concept-id \[\w+-\w+\] and native-id \[.*?\] would conflict with that one."])))))

    (testing "save after delete"
      (let [concept (gen-concept concept-type provider-id 9 {})
            {:keys [concept-id]} (util/save-concept concept)]
        (is (= 201 (:status (util/delete-concept concept-id))))
        (save-concept-test concept 201 3 nil 3)))

    (testing "save after delete with invalid revision fails"
      (let [concept (gen-concept concept-type provider-id 10 {})
            {:keys [concept-id]} (util/save-concept concept)]
        (is (= 201 (:status (util/delete-concept concept-id))))
        (save-concept-test
          (assoc concept :revision-id 1)
          409
          nil
          [#"Expected revision-id of \[3\] got \[1\] for \[\w+-\w+\]"])))

    (testing "auto-increment of revision-id with skipped revisions"
      (let [concept (gen-concept concept-type provider-id 11 {})
            {:keys [concept-id]} (util/save-concept concept)
            concept-with-concept-id (assoc concept :concept-id concept-id)
            _ (util/save-concept (assoc concept-with-concept-id :revision-id 100))
            {:keys [status revision-id]} (util/save-concept concept-with-concept-id)
            {retrieved-concept :concept} (util/get-concept-by-id concept-id)]
        (is (= 201 status))
        (is (= 101 revision-id (:revision-id retrieved-concept)))))

    (testing "save concept results in incremented transaction-id"
      (let [concept (gen-concept concept-type provider-id 12 {})]
        (loop [index 0
               transaction-id 0]
          (when (< index 10)
            (let [{:keys [concept-id revision-id]} (util/save-concept concept)
                  retrieved-concept (util/get-concept-by-id-and-revision concept-id revision-id)
                  new-transaction-id (get-in retrieved-concept [:concept :transaction-id])]
              (is (> new-transaction-id transaction-id))
              (recur (inc index) new-transaction-id))))))))
