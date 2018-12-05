(ns cmr.nlp.core
  (:require
   [clojure.string :as string]
   [cmr.nlp.time.human :as human-time]
   [cmr.nlp.util :as util]
   [opennlp.nlp :as nlp]
   [opennlp.treebank :as treebank])
  (:refer-clojure :exclude [chunk]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API based upon OpenNLP Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(def -find-dates
  (nlp/make-name-finder
    (util/get-model "en-ner-date")))

(defn find-dates
  ([tokens]
    (find-dates tokens {}))
  ([tokens opts]
    (if (:as-datestamp opts)
      (-find-dates tokens)
      (-find-dates tokens))))

(def find-times
  (nlp/make-name-finder
    (util/get-model "en-ner-time")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API from Compound Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-dates
  [sentence]
  (let [parser (human-time/create)
        dates (map #(human-time/date parser %)
                   (find-dates (tokenize sentence)))]
    (if-not (empty? dates)
      (sort dates)
      [(human-time/date parser sentence)])))

(comment
  (def t (nlp/tokenize "between last year and two years ago"))
  (def dates (nlp/find-dates t))

  (def p (human-time/create))
  (map #(human-time/dates p %) dates))
