(ns cmr.umm.test.generators.collection.science-keyword
  "Provides clojure.test.check generators for use in testing other science-keywords"
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.umm-collection :as c]))

(def categories
  "SMAP ISO requires the category be one of 'EARTH SCIENCE' or 'EARTH SCIENCE SERVICES',
  so we limit the category values here for ease of testing."
  (gen/one-of [(gen/return "EARTH SCIENCE")
               (gen/return "EARTH SCIENCE SERVICES")]))

(def topics
  (ext-gen/string-alpha-numeric 1 10))

(def terms
  (ext-gen/string-alpha-numeric 1 10))

(def variable-levels
  (gen/vector (ext-gen/string-alpha-numeric 1 10) 1 3))

(def detailed-variables
  (ext-gen/optional (ext-gen/string-alpha-numeric 1 10)))

(def science-keywords
  (gen/fmap
    (fn [[category topic term [variable-level-1 variable-level-2 variable-level-3] detailed-variable]]
      (c/->ScienceKeyword
        category
        topic
        term
        variable-level-1
        variable-level-2
        variable-level-3
        detailed-variable))
    (gen/tuple categories topics terms variable-levels detailed-variables)))
