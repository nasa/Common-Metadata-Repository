(ns cmr.umm.test.generators.collection.science-keyword
  "Provides clojure.test.check generators for use in testing other science-keywords"
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]))

(def categories
  (ext-gen/string-ascii 1 500))

(def topics
  (ext-gen/string-ascii 1 500))

(def terms
  (ext-gen/string-ascii 1 500))

(def variable-levels
  (gen/vector (ext-gen/string-ascii 1 500) 1 3))

(def detailed-variables
  (ext-gen/optional (ext-gen/string-ascii 1 80)))

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
