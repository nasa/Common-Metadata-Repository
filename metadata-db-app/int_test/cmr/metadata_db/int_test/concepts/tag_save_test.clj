(ns cmr.metadata-db.int-test.concepts.tag-save-test
  "Contains integration tests for saving tags. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.concept-constraints :as cc]))


;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Reset - no need for providers yet (we use "cmr" internally). We will need to add
;; providers for later tests when we want to associate collections with tags.
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV"}))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-tag-test
  (let [tag (util/tag-concept 1)
        {:keys [status revision-id concept-id] :as resp} (util/save-concept tag)]
    (is (= 201 status) (pr-str resp))
    (is (= revision-id 1))
    (is (util/verify-concept-was-saved (assoc tag :revision-id revision-id :concept-id concept-id)))))