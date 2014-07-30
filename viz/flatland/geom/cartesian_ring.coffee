class window.CartesianRing extends Module

  @LINE_STYLE = {straightFirst:false, straightLast:false, strokeWidth:1}

  constructor: (@points, options={}) ->
    super()
    @id = options.id

    # Determine if the points self close
    # Remove last point if it does
    if @points[0].equals(@points[@points.length - 1])
      @points = @points[0..-2]
      @closed = true
    else
      @closed = false

  # Converts a string of points lon,lat,lon,lat to a ring
  @fromOrdinates: (ordinates, options={})->
    new CartesianRing(Point.fromOrdinates(ordinates), options)

  toOrdinates: ->
    Point.toOrdinates(this.getPoints())

  # Returns a string of comma separate lon,lat,lon,lat...
  # for all the points in the ring.
  toOrdinatesString: (options={}) ->
    Point.toOrdinatesString(this.getPoints(), options)

  display: (board)->
    p.display(board) for p in @points
    unless @lines
      displayedPoints = _.map(@points, (p)-> p.displayedPoint)
      @lines = []
      _.eachCons(displayedPoints, 2, (pair)=>
        @lines.push(board.create('line', displayedPoints, CartesianRing.LINE_STYLE)))
      if @closed
        @lines.push(board.create("line", [displayedPoints[0], displayedPoints[displayedPoints.length - 1]]))

  undisplay: (board)->
    p.undisplay(board) for p in @points
    if @lines
      board.removeObject(line) for line in @lines
      @lines = null

