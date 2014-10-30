(ns cmr.common.dev.capture-reveal
  "A prototype namespace that helps with debugging. It can easily capture and reveal values.

  If you use SublimeText with the typical CMR developer setup you can setup the following key bindings:

  // Reveal highlighted captured var value
  { \"keys\": [\"alt+super+v\"], \"command\": \"run_on_selection_in_repl\", \"args\": {\"function\": \"cmr.common.dev.capture-reveal/reveal\"}},
  { \"keys\": [\"alt+super+shift+v\"], \"command\": \"run_command_in_namespace_in_repl\", \"args\": {\"command\": \"(cmr.common.dev.capture-reveal/reveal-all)\"}},

  The key bindings will make cmd+alt+v print out the captured value of the selected symbol.
  cmd+shift+alt+v will print out all of the captured symbols from the namespace of the current open
  file. (Your cursor must be in that file)

  You can make it easy to insert the text for a captured value by creating the following files:

  ~/Library/Application Support/Sublime Text 2/Packages/User/capture.sublime-snippet

<snippet>
  <content><![CDATA[
cmr.common.dev.capture-reveal/capture
]]></content>
  <tabTrigger>capture</tabTrigger>
  <scope>source.clojure</scope>
</snippet>

  ~/Library/Application Support/Sublime Text 2/Packages/User/capture-reveal.sublime-snippet

<snippet>
  <content><![CDATA[
cmr.common.dev.capture-reveal/reveal
]]></content>
  <tabTrigger>reveal</tabTrigger>
  <scope>source.clojure</scope>
</snippet>

  After typing in those files 'capture' or 'reveal' typed into a clojure file will give the option
  of autocompleting to the text shown above.")

;; TODO idea for extension
;; Consider that in sublime we like to execute blocks of code using cmd+alt+b. Often times the variables
;; for the block were passed in somewhere above that. We could capture the variables and then add
;; a new keystroke (cmd+alt+shift+b) that will execute the block with all the captured vars set
;; in an implicit let.

(def captured-values
  "Contains the captured values. This is a map of namespace names to a map of captured value symbols
  to captured values"
  (atom {}))

(defn capture-values
  "Stores the captured var-sym-values in the capture-values by namespace. var-sym-values should be
  an alternating list of symbols and their values. the-ns should be the namesspace string name."
  [the-ns & var-sym-values]
  ;; Create a map of var symbols to var values.
  (let [var-sym-values-map (into {} (map vec (partition 2 var-sym-values)))]
    (swap! captured-values
           (fn [captured]
             (update-in captured [the-ns] merge var-sym-values-map)))))

(defmacro capture
  "This is a helper macro for easily capturing values. It will grab the current namespace.

  Call it like this:

      (let [foo 5]
        (cmr.common.dev.capture-reveal/capture foo))"
  [& vars]
  `(capture-values ~(str *ns*)
                   ;; Create a sequence of var symbols to the var values.
                   ~@(mapcat (fn [v] [`'~v v]) vars)))

(defn reveal-value
  "Gets the captured value of the given symbol in the namespace."
  [the-ns var-sym]
  (get-in @captured-values [the-ns var-sym]))

(defn reveal-all-values
  "Returns all the captured values for a namespace."
  [the-ns]
  (get @captured-values the-ns))

(defmacro reveal
  "Returns the last captured value of a symbol in the current namespace.

  Call it like this:

      (cmr.common.dev.capture-reveal/reveal foo)"
  [var-sym]
  `(reveal-value ~(str *ns*) '~var-sym))

(defmacro reveal-all
  "Returns all the captured values for the current namespace."
  []
  `(reveal-all-values ~(str *ns*)))

