(ns cmr.sizing.tests.unit.util
  (:require
   [clojure.test :refer :all]
   [cmr.sizing.util :as util]))

(deftest kb->bytes
  (is (= 1024.0 (util/kb->bytes 1))))

(deftest mb->bytes
  (is (= 1048576.0 (util/mb->bytes 1))))

(deftest bytes->mb
  (is (= 1.0 (util/bytes->mb 1048576))))

(deftest bytes->gb
  (is (= 1.0 (util/bytes->gb 1073741824))))
