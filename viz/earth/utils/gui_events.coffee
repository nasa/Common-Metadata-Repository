window.GuiEventEmitter = 
  addGuiEventListener: (listener) ->
    @guiListeners = [] unless @guiListeners
    @guiListeners.push listener

  removeGuiEventListener: (listener) ->
    @guiListeners = _.reject(@guiListeners, (l) -> l == listener)

  notifyGuiEventListeners: (event) ->
    if @guiListeners
      listener.handleGuiEvent(event) for listener in @guiListeners
    null

  handleGuiEvent: (event) ->
    this.notifyGuiEventListeners(event)

