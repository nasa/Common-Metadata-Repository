(ns cmr.umm.validation.validation-helper
  "Contains helper functions for validation."
  (:require [cmr.common.validations.core :as v]
            [cmr.umm.validation.validation-utils :as vu]
            [cmr.umm.related-url-helper :as ruh]))

(def online-access-urls-validation
  "Defines online access urls validation for collections."
  (v/pre-validation
    ruh/downloadable-urls
    (vu/unique-by-name-validator :url)))



