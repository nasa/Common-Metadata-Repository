# Allows an array to be iterated through just like each_slice in ruby
# list = [1,2,3,4]
# _.eachSlice(list, 2, (pair) ->
#    console.log pair)
# --> "1,2"
# --> "3,4"
slicer = (obj, size, iterator, context) ->
  for i in [0..obj.length-1] by size
    slice = obj[i...i+size]
    iterator.call(context, slice, i/2, obj)
_.mixin({"eachSlice": slicer})

# Allows an array to be iterated through just like each_cons in ruby
# list = [1,2,3,4]
# _.eachCons(list, 2, (pair) ->
#    console.log pair)
# --> "1,2"
# --> "2,3"
# --> "3,4"
conser = (obj, size, iterator, context) ->
  for i in [0..obj.length-size]
    stop = i + size
    stop = obj.length if stop > obj.length
    slice = obj[i...stop]
    iterator.call(context, slice, i, obj)
_.mixin({"eachCons": conser})

# Joins together items from an array using the given separator.
# list = [1,2,3,4]
# _.join(list, ",")
# --> "1,2,3,4"
joiner  = (obj, separator, iterator, context) ->
  str = ""
  first = true
  _.reduce(obj, (memo, i) -> 
    if first
      first = false
      memo + i 
    else
      memo + separator + i
  "")
_.mixin({"join": joiner})

Math.degreesToRadians = (d) ->
  d * (Math.PI / 180.0)

Math.radiansToDegrees = (r) ->
  r * (180 / Math.PI)

# Javascripts modulus operator is broken for negative numbers. This is a correct implementation.
# http://javascript.about.com/od/problemsolving/a/modulobug.htm
Math.mod = (d, n) ->
  ( (d % n) + n ) % n

# Rounds the number to the given number of digits
Math.roundTo = (num, numDigits) ->
  power = Math.pow(10, numDigits)
  Math.round(num*power)/power

# My version of $ or _. It has some utility functions.
window.Sp =   
  rootUrl: ->
    $('body').attr('data-root')

  clone: () ->
    JSON.parse(JSON.stringify(object))

  # A helper that allows functions to be used in place of values in certain circumstances
  # This returns either the result of the function or the object itself.
  fnValue: (object) ->
    if _.isFunction(object)
      object()
    else
      # Calling valueOf avoids converting primitive objects to their class equivalents.
      object.valueOf()

# Add a function to jquery to make it easy to get json back from a post
jQuery.extend({
  getJSONPost: (url, data, callback) ->
    jQuery.post(url, data, callback, "json")
})

