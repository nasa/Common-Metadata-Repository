window.GoogleEarthEventHandler = 
  handleMouseDown: (event, ge) ->
    # does nothing by default

  handleMouseMove: (event, ge) ->
    # does nothing by default
    
  handleMouseUp: (event, ge) ->
    # does nothing by default

  handleDoubleClick: (event, ge) ->
    # does nothing by default

  handleMouseOver: (event, ge) ->
    # does nothing by default

  handleClick: (event, ge) ->
    # does nothing by default


window.GoogleEarthEventEmitter = 
  
  # Everything but the GuiEvent is from the Google Earth plugin. GuiEvent is used for events
  # triggered by the gui controls itself like dragging a point.
  EVENT_TYPES: ["MouseDown", "MouseMove", "MouseUp", "DoubleClick", "MouseOver", "Click"]

  addEventListener: (listener)->
    @listeners = [] unless @listeners
    @listeners.push(listener)

  removeEventListener: (listener) ->
    @listeners = _.reject(@listeners, (l) -> l == listener)

# Use metaprogramming to define all these methods with similar behavior
_.each(GoogleEarthEventEmitter.EVENT_TYPES, (type) ->
  methodName = "handle#{type}"
  GoogleEarthEventEmitter[methodName] = (event_and_others...) -> 
    if @listeners
      l[methodName](event_and_others...) for l in @listeners
    null
)


