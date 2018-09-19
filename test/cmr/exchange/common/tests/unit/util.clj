(ns cmr.exchange.common.tests.unit.util
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.exchange.common.util :as util]))

(deftest deep-merge
  (is (= {} (util/deep-merge {} {})))
  (is (= {:a 2} (util/deep-merge {:a 1} {:a 2})))
  (is (= {:a 1 :b 2} (util/deep-merge {:a 1} {:b 2})))
  (is (= {:a 2} (util/deep-merge {:a {:b {:c {:d 1}}}} {:a 2})))
  (is (= {:a {:b 2}} (util/deep-merge {:a {:b {:c {:d 1}}}} {:a {:b 2}})))
  (is (= {:a {:b {:c {:d 1}}} :e 2}
         (util/deep-merge {:a {:b {:c {:d 1}}}} {:e 2})))
  (is (= {:a {:b {:c {:d 1}}
              :e {:f {:g 1}}}}
         (util/deep-merge {:a {:b {:c {:d 1}}}}
                          {:a {:e {:f {:g 1}}}})))
  (is (= {:a {:b {:c {:d 1}
                  :e {:f 1}}}}
         (util/deep-merge {:a {:b {:c {:d 1}}}}
                          {:a {:b {:e {:f 1}}}})))
  (is (= {:a {:b {:c {:d 1
                      :e 1}}}}
         (util/deep-merge {:a {:b {:c {:d 1}}}}
                          {:a {:b {:c {:e 1}}}}))))
