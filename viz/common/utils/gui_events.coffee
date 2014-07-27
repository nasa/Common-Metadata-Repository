window.GuiEventEmitter =
  addGuiEventListener: (listener) ->
    @guiListeners = [] unless @guiListeners
    @guiListeners.push listener

  removeGuiEventListener: (listener) ->
    @guiListeners = _.reject(@guiListeners, (l) -> l == listener)

  notifyGuiEventListeners: (event, ge) ->
    if @guiListeners
      listener.handleGuiEvent(event, ge) for listener in @guiListeners
    null

  handleGuiEvent: (event, ge) ->
    this.notifyGuiEventListeners(event, ge)

