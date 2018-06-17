(ns cmr.authz.tests.unit.errors
  "Note: this namespace is exclusively for unit tests."
  (:require
   [clojure.test :refer :all]
   [cmr.authz.errors :as errors]))

(deftest any-errors?
  (is (not (errors/any-errors? {:errors []})))
  (is (not (errors/any-errors? {:errors ["Oops"]})))
  (is (errors/any-errors? {:errors ["Oops"
                                    errors/no-permissions]}))
  (is (errors/any-errors? {:errors [errors/no-permissions]})))
