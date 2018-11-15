(ns cmr.nlp.core
  (:require
   [clojure.string :as string]
   [cmr.nlp.util :as util]
   [opennlp.nlp :as nlp]
   [opennlp.treebank :as treebank])
  (:refer-clojure :exclude [chunk]))

(def tokenize
  (comp (nlp/make-tokenizer (util/get-model "en-token"))
        util/close-sentence))

(def tag-pos
  (nlp/make-pos-tagger
    (util/get-model "en-pos-maxent")))

(def chunk
  (treebank/make-treebank-chunker
    (util/get-model "en-chunker")))

(def parser
  (treebank/make-treebank-parser
    (util/get-model "en-parser-chunking")))

(def find-locations
  (nlp/make-name-finder
    (util/get-model "en-ner-location")))

(def find-dates
  (nlp/make-name-finder
    (util/get-model "en-ner-date")))

(def find-times
  (nlp/make-name-finder
    (util/get-model "en-ner-time")))
