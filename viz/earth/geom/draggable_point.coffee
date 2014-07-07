class window.DraggablePoint extends Point
  @include GuiEventEmitter

  @MOVE_EVENT = "move_point"
  @DRAG_FINISH_EVENT = "drag_finish_point"

  # Create a constant event so we don't have to create one every time
  @MOVE_EVENT_CACHED = new Event(@MOVE_EVENT)
  @DRAG_FINISH_EVENT_CACHED = new Event(@DRAG_FINISH_EVENT)

  constructor: (lon, lat, options={}) ->
    super(lon, lat, options)
    @callbackFn = options.callbackFn if options.callbackFn
    @dragging = false

  @fromOrdinates: (ordinates)->
    Point.fromOrdinates(ordinates, DraggablePoint)

  # updates the location of the point and notifies listeners
  setLonAndLat: (ge, lon,lat, notify=true) ->
    @lat = lat
    @lon = lon
    this.placemark.setLonAndLat(lon,lat)
    if notify
      this.notifyGuiEventListeners(DraggablePoint.MOVE_EVENT_CACHED, ge)

  # Makes the point move to a position rounded on lat and lon
  # of numDigits
  snapToGrid: (ge, numDigits=2) ->
    power = Math.pow(10, numDigits)
    lon = Math.round(@lon*power)/power
    lat = Math.round(@lat*power)/power
    this.setLonAndLat(ge, lon,lat)

  # Handles mouse events
  handleMouseDown: (event, ge) ->
    if @placemark.isEventTarget(event)
      @dragging = true
      @moved = false

  handleMouseMove: (event, ge) ->
    if @dragging
      @moved = true
      this.setLonAndLat(ge, event.getLongitude(), event.getLatitude())
      event.preventDefault()

  handleMouseUp: (event, ge) ->
    if @dragging
      @dragging = false
      if @moved
        this.snapToGrid(ge)
        this.notifyGuiEventListeners(DraggablePoint.DRAG_FINISH_EVENT_CACHED, ge)
        if @callbackFn
          pointStr = "#{@lon},#{@lat}"
          if @id && @id != null
            callbackStr = "#{@id}:#{pointStr}"
          else
            callbackStr = pointStr
          console.log("Calling callback #{@callbackFn} with #{callbackStr}")
          vdd_core.connection.callServerFunction(window.vddSession, @callbackFn, callbackStr)

  handleClick: (event, ge) ->
    super(event, ge) unless @moved
    @moved = false

