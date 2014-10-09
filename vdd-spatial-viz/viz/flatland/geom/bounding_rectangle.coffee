class window.BoundingRectangle extends Module

  @POINT_STYLE = {size: 2}
  @POLYGON_STYLE = {fillOpacity: 0.15}

  constructor: (@west, @north, @east, @south, options={}) ->
    super()
    @id = options.id
    # multiple placemarks are used so we can handle crossing antimeridian
    @box1 = null
    @box2 = null

    # Make a zero height bounding rectangle slightly taller so it's visible
    if @north == @south
      if @north == 90
        @south = @south - 0.01
      else
        @north = @north + 0.01

  @fromObject: (data)->
    new BoundingRectangle(data.west, data.north, data.east, data.south)

  display: (board) ->
    if this.crossesAntimeridian()
      @shapes1 = this.createBoxPolygon(board, @west, 180)
      @shapes2 = this.createBoxPolygon(board, -180, @east)
    else
      @shapes1 = this.createBoxPolygon(board, @west, @east)
      @shapes2 = null

  undisplay: (board) ->
    if @shapes1 != null
      board.removeObject(s) for s in @shapes1
      @shapes1 = null

    if @shapes2 != null
      board.removeObject(s) for s in @shapes2
      @shapes2 = null

  crossesAntimeridian: ->
    @west > @east

  # A helper that creates a polygon placemark with the given bounds.
  createBoxPolygon: (board, west, east, north=@north, south=@south)->
    ul = board.create("point", [west, north], BoundingRectangle.POINT_STYLE)
    ur = board.create("point", [east, north], BoundingRectangle.POINT_STYLE)
    lr = board.create("point", [east, south], BoundingRectangle.POINT_STYLE)
    ll = board.create("point", [west, south], BoundingRectangle.POINT_STYLE)

    polygon = board.create("polygon", [ul, ur, lr, ll], BoundingRectangle.POLYGON_STYLE)

    [ul, ur, lr, ll, polygon]



