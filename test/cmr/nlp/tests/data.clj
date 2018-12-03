(ns cmr.nlp.tests.data
  (:require
    [cmr.nlp.core :as nlp]))

(def spatio-temporal-sentence-1
  "What was the average surface temperature of Lake Superior last week?")

(def spatio-temporal-tokens-1
  (nlp/tokenize spatio-temporal-sentence-1))
