(ns cmr.search.test.services.query-execution.facets.links-helper
  "Unit tests for facets links helper namespace."
  (:require [cmr.search.services.query-execution.facets.links-helper :as lh]))

;; TODO Add tests
;; Multiple science keywords or'ed together and and-ed together - each of the subfields in the hierarchy
;; Apply links for each of the different types
;; Remove links for each of the different types
;; Empty facets
;; Results where some facets are present and some are not

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

; (deftest apply-science-keyword-link
;   (let))
