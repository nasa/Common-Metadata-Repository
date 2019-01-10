(ns cmr.nlp.tests.unit.util
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.nlp.util :as util]
    [cmr.nlp.tests.data :as test-data]))

(deftest date->cmr-date-string
  (let [date (.parse (util/simple-date-formatter) "1850-01-01")]
    (is (= "1850-01-01T00:00:00Z"
           (util/date->cmr-date-string date)))))

(deftest encode-tuple
  (is (= "foo=bar"
         (util/encode-tuple ["foo" "bar"]))))

(deftest encode-tuples
  (is (= "foo=bar&baz=quux"
         (util/encode-tuples [["foo" "bar"] ["baz" "quux"]]))))
