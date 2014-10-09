class window.Point extends Module

  @include GuiEventEmitter

  @DRAG_FINISH_EVENT = "drag_finish_point"
  @DRAG_FINISH_EVENT_CACHED = new Event(@DRAG_FINISH_EVENT)

  constructor: (@lon, @lat, options={}) ->
    super()
    @lon = parseFloat(@lon)
    @lat = parseFloat(@lat)
    @id = options.id
    # A clojure function to call on the server when the ring is dragged
    @callbackFn = options.callbackFn if options.callbackFn

  @fromOrdinates: (ordinates, type=Point)->
    points = []
    _.eachSlice(ordinates, 2, (slice, i) ->
      options =
        label: (i+1).toString()
      points.push(new type(slice[0], slice[1], options))
    )
    points

  @stringToOrdinates: (ordinates_string) ->
    _.map(ordinates_string.split(/\s*,\s*/), (o) -> parseFloat(o))

  @toOrdinates: (points) ->
    _.reduce(points, (memo, p) ->
      memo.push(p.lon)
      memo.push(p.lat)
      memo
    ,[])

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  @toOrdinatesString: (points, options={})->
    if options.includeSpace
      join_str = ", "
    else
      join_str = ","

    ordinate_pairs = _.reduce(points, ((a,p)->
      a.push "#{p.lon},#{p.lat}"
      a
      a), [])
    ordinate_pairs.join(join_str)

   # Handles mouse events
  handleMouseDown: (event, board) ->
    @dragging = true
    @moved = false

  handleMouseMove: (event, board) ->
    if @dragging
      @moved = true
      @lon = @displayedPoint.X()
      @lat = @displayedPoint.Y()

  handleMouseUp: (event, board) ->
    if @dragging
      @dragging = false
      if @moved
        this.notifyGuiEventListeners(Point.DRAG_FINISH_EVENT_CACHED, board)
        if @callbackFn
          pointStr = "#{@lon},#{@lat}"
          if @id && @id != null
            callbackStr = "#{@id}:#{pointStr}"
          else
            callbackStr = pointStr
          console.log("Calling callback #{@callbackFn} with #{callbackStr}")
          vdd_core.connection.callServerFunction(window.vddSession, @callbackFn, callbackStr)

  display: (board)->
    unless @displayedPoint
      @displayedPoint = board.create('point',[@lon,@lat], {name:"", size:3})
      @displayedPoint.on("mousedown", (e)=> @handleMouseDown(e, board))
      @displayedPoint.on("mousedrag", (e)=> @handleMouseMove(e, board))
      @displayedPoint.on("mouseup", (e)=> @handleMouseUp(e, board))

  undisplay: (board) ->
    if @displayedPoint
      board.removeObject(@displayedPoint)
      @displayedPoint = null

  equals: (p) ->
    @lon == p.lon and @lat == p.lat