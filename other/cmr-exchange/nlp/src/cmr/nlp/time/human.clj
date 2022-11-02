(ns cmr.nlp.time.human
  (:require
    [clojure.string :as string])
  (:import
    (org.ocpsoft.prettytime.nlp PrettyTimeParser
                                PrettyTimeParser$DateGroupImpl))
  (:refer-clojure :exclude [parse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn alpha-numberic
  [sentence]
  (string/replace sentence #"[^A-Za-z0-9\s]" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol HumanTimeAPI
  (parse [this sentence])
  (dates [this sentence])
  (date [this sentence]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -parse
  [this sentence]
  (->> sentence
       alpha-numberic
       (.parseSyntax this)))

;; XXX In a handful of test cases, PrettyTimeParser.getDates doesn't seem to
;;     extract multiple dates ... and neither does .parseSyntax. The date
;;     method will be offered as a work-around for this, with the expectation
;;     that it should only be called with a sentence (fragment) that contains
;;     one date reference.
(defn -dates
  [this sentence]
  (map #(.getDates %) (-parse this sentence)))

(def behaviour
  {:dates -dates
   :date #(ffirst (-dates %1 %2))
   :parse -parse})

(extend PrettyTimeParser
        HumanTimeAPI
        behaviour)

(extend PrettyTimeParser$DateGroupImpl
        HumanTimeAPI
        behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  []
  (new PrettyTimeParser))

(comment
  (def s "What was that thing 20 years ago?")
  (human-time/alpha-numberic s)
  (def p (human-time/create))
  (human-time/dates p s))
