(ns cmr.umm-spec.umm-to-xml-mappings.dsl
  "Defines a DSL for generating XML from a source Clojure record.

  The DSL is used to specify content generators. Content generators generate parts of XML which could
  be single elements, multiple elements, or strings in the XML. Content generators can take any of
  the following forms.

  ## Vector

  example: [:foo [:bar \"here\"]] => <foo><bar>here</bar></foo>

  A vector specifies that an element should be created. The first element of the vector is the tag
  name of the element. The rest of the contents of the vector are treated as a list of content
  generators that specify the content of the element.

  Keywords may be used as a shorthand for a child element whose contents are to be taken from the
  corresponding path in the current context, as a convenience for formats which map closely to the
  UMM model itself. e.g.:

      [:foo :bar bat]
      ;; is equivalent to
      [:foo [:bar (xpath \"bar\")] [:bat (xpath \"bat\")]]

  A map immediately following the tag name (without a ::type) will be treated as attributes for the
  element. The attributes map is treated as a map of attribute names to content generators for each
  attribute.

  ## String

  example: [:foo \"here\"] => <foo>here</foo>

  A string is treated as literal content and is usually used inside of another content generator.

  ## Map with a :type key

  Maps with a namespaced type key are used to specifying various other types of content generators
  with specific parameters that will be stored in the map as other keys. The maps are usually
  constructed with functions from this namespace. See individual functions for documentation

  ## Functions

  Functions may be supplied wherever a content generator is expected. The function will be called
  with the current xpath context, and must return a sequence of resulting elements.")


(defn xpath
  "Specifies a content generator that pulls a value from the source Clojure data at a given XPath."
  [value]
  {::type :xpath :value value})


(defn for-each
  "Specifies a content generator that pulls multiple values from the source Clojure data at the given
  XPath. The template should be another content generator that will be used to generate values
  for each of the values found at the xpath."
  [xpath template]
  {::type :for-each
   :xpath xpath
   :template template})

(defn char-string-from
  "Defines a mapping for a ISO CharacterString element with a value from the given XPath."
  [xpath-str]
  [:gco:CharacterString (xpath xpath-str)])

(defn char-string
  "Defines a mapping for a ISO CharacterString element with the given value."
  [value]
  [:gco:CharacterString value])

(defn simple-field
  "Returns an element mapping with an XML element name matching the corresponding UMM field."
  [k]
  [k (xpath (name k))])

(defn simple-object
  "Returns an element named by tag, with child XML element names matching the corresponding UMM
  fields."
  [tag & ks]
  (vec (cons tag (map simple-field ks))))
