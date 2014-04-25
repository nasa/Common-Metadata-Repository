(ns cmr.system-int-test.data.collection-helper-test
  "Tests generating UMM collection."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.data.collection-helper :as ch]
            [cmr.common.date-time-parser :as p]))

(deftest generated-collection-has-correct-field-value-test
  (let [provided-entry-title "A minimal valid collection V 1"
        provided-short-name "MINIMAL"
        provided-long-name "A minimal valid collection"
        provided-version-id "1"
        provided-beginning-date-time "1996-02-24T22:20:41-05:00"
        provided-ending-date-time "1997-03-24T22:20:41-05:00"

        actual (ch/collection {:entry-title provided-entry-title
                               :short-name provided-short-name
                               :long-name provided-long-name
                               :version-id provided-version-id
                               :beginning-date-time provided-beginning-date-time
                               :ending-date-time provided-ending-date-time})

        {:keys [entry-title product temporal]} actual
        {:keys [short-name long-name version-id]} product
        {:keys [range-date-times]} temporal]

    (is (= provided-entry-title entry-title))
    (is (= provided-short-name short-name))
    (is (= provided-long-name long-name))
    (is (= provided-version-id version-id))
    (is (= 1 (count range-date-times)))
    (is (= (p/string->datetime provided-beginning-date-time) (:beginning-date-time (first range-date-times))))
    (is (= (p/string->datetime provided-ending-date-time) (:ending-date-time (first range-date-times))))))
