(ns cmr.umm-spec.xml-mappings.dsl
  "Defines a DSL for generating XML from a source Clojure record.

  The DSL is used to specify content generators. Content generators generate parts of XML which could
  be single elements, multiple elements, or strings in the XML. Content generators can take any of
  the following forms.

  ## Vector

  example: [:foo [:bar \"here\"]] => <foo><bar>here</bar></foo>

  A vector specifies that an element should be created. The first element of the vector is the tag
  name of the element. The rest of the contents of the vector are treated as a list of content
  generators that specify the content of the element.

  Note that a inner map with a :type of :attribs are used to specify the attributes on an element.

  ## String

  example: [:foo \"here\"] => <foo>here</foo>

  A string is treated as literal content and is usually used inside of another content generator.

  ## Map with a :type key

  Maps with a type key are used to specifying various other types of content generators with specific
  parameters that will be stored in the map as other keys. The maps are usually constructed with
  functions from this namespace. See individual functions for documentation")

(defn xpath
  "Specifies a content generator that pulls a value from the source XML document at a given XPath."
  [value]
  {:type :xpath :value value})


(defn for-each
  "Specifies a content generator that pulls multiple values from a source XML document at the given
  XPath. The template should be another content generator that will be used to generate values
  for each of the values found at the xpath."
  [xpath template]
  {:type :for-each
   :xpath xpath
   :template template})

(defn attribs
  "Specifies the attributes of an element. Must be used within an element vector. It can appear
  anywhere within the element vector. Multiple can be used and their values will be merged. The
  attributes argument is treated as a map of keywords to content generators for each attribute."
  [attributes]
  {:type :attribs
   :value attributes})

