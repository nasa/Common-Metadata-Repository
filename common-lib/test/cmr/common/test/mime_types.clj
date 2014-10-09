(ns cmr.common.test.mime-types
  "Tests for mime-type functions."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [cmr.common.mime-types :as mt]))

(def unsupported-mime-types
  "Other mime-types."
  #{"text/html"
    "text/xml"
    "application/xhtml+xml"
    "applicaiton/dart"
    "application/javascript"
    "application/ecmascript"
    "application/pdf"})

(def supported-mime-types
  "The mime-types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"
    "application/echo10+xml"
    "application/dif+xml"
    "application/atom+xml"
    "application/iso+xml"
    "application/iso-mends+xml"
    "application/iso:smap+xml"
    "text/csv"})

(def optional-params
  "Some optional parameters to construct semicolon clauses."
  #{"charset=UTF-8"
    "charset=UTF-16"
    "header=present"
    ""})

(def all-mime-types
  (gen/elements (concat unsupported-mime-types supported-mime-types)))

(def mime-type-clause
  (gen/fmap (fn [[type params]]
              (if (empty? params)
                type
                (str type "; " params)))
            (gen/tuple all-mime-types (gen/elements optional-params))))

(def mime-type-strings
  (gen/fmap (fn [mime-type-vec] (str/join "," mime-type-vec))
            (gen/vector mime-type-clause)))

(defspec mime-type-from-headers 100
  (for-all [mime-type-str mime-type-strings]
    (let [headers {"accept" mime-type-str}
          mime-type (mt/mime-type-from-headers headers supported-mime-types)]
      (or (supported-mime-types mime-type)
          (= mime-type-str mime-type)
          (and (nil? mime-type)
               (empty? mime-type-str))))))