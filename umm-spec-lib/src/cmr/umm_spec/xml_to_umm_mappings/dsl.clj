(ns cmr.umm-spec.xml-to-umm-mappings.dsl
  "Describes a DSL for specifying mappings from an XML format into UMM records.

  The UMM record structure is described by the JSON schema. The DSL is responsible for specifying
  how to construct the UMM records from the source document. This is done through a series of nested
  maps with different :type attributes. The structure of the maps should mirror that of the JSON
  schema. This allows the addition of parsing types which give instructions to the parser on what
  Clojure object to construct when parsing from the XML.

  You may also provide a function anywhere a parsing type map is allowed. The function will be
  called with the current xpath-context and should return the desired parsed value.")

(defn object
  "Defines a mapping for an object with the given properties map. A UMM record will be instantiated
  based on the associated parse type."
  [properties]
  {:type :object
   :properties properties})

(defn xpath
  "Defines a mapping from a value at a specific XPath into the XML. The value from the XPath
  will be parsed based on the associated type in the schema."
  [value]
  {:type :xpath :value value})

(defn constant
  "Defines a mapping that returns a constant value"
  [value]
  {:type :constant :value value})

(defn concat-parts
  "Defines a mapping that returns a concatenation of other parts. The parts will all be evaluated
  with parse type of string."
  [& parts]
  {:type :concat
   :parts (vec parts)})

(defn for-each
  "Defines a mapping that uses a mapping template to parse each value at the given XPath."
  [xpath template]
  {:type :for-each
   :xpath xpath
   :template template})

(defn select
  "Similar to for-each except that no template is given. Used for XPaths that are expected to return
  multiple primitive types"
  [xpath]
  {:type :for-each
   :xpath xpath})

(defn char-string-xpath
  "An ISO Xpath helper. It creates an XPath that selects from the given XPath with a
  /gco:CharacterString child. Optionally accepts a base XPath."
  ([path]
   (char-string-xpath "" path))
  ([base-xpath path]
   (xpath (str base-xpath path "/gco:CharacterString"))))

