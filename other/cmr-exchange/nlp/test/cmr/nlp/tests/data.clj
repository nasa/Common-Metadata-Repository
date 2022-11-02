(ns cmr.nlp.tests.data
  (:require
    [cmr.nlp.core :as nlp]))

(def spatio-temporal-sentence-1
  "What was the average surface temperature of Lake Superior last week?")

(def spatio-temporal-tokens-1
  (nlp/tokenize spatio-temporal-sentence-1))

(def spatio-temporal-sentence-2
  "What was the average annual snowfall in Alaska 20 years ago?")

(def spatio-temporal-tokens-2
  (nlp/tokenize spatio-temporal-sentence-2))

(def relative-temporals-sentence-1
  "What was that thing that happened between last year and two years ago?")

(def relative-future-temporals-sentence-1
  "What was that thing you planned for next Thursday?")
