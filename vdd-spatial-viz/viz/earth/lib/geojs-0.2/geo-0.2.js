/*
Copyright 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
(function() {
/**
 * The geo namespace contains generic classes and namespaces for processing
 * geographic data in JavaScript. Where possible, an effort was made to keep
 * the library compatible with the Google Geo APIs (Maps, Earth, KML, etc.)
 * @namespace
 */
var geo = {isnamespace_:true};
/*
see https://developer.mozilla.org/En/Core_JavaScript_1.5_Reference:Objects:Array:map
*/
//#JSCOVERAGE_IF !('map' in Array.prototype)
if (!('map' in Array.prototype)) {
  Array.prototype.map = function(mapFn) {
    var len = this.length;
    if (typeof mapFn != 'function') {
      throw new TypeError('map() requires a mapping function.');
    }

    var res = new Array(len);
    var thisp = arguments[1];
    for (var i = 0; i < len; i++) {
      if (i in this) {
        res[i] = mapFn.call(thisp, this[i], i, this);
      }
    }

    return res;
  };
}
//#JSCOVERAGE_ENDIF
// TODO: geo.ALTITUDE_NONE to differentiate 2D/3D coordinates
geo.ALTITUDE_CLAMP_TO_GROUND = 0;
geo.ALTITUDE_RELATIVE_TO_GROUND = 1;
geo.ALTITUDE_ABSOLUTE = 2;
geo.ALTITUDE_CLAMP_TO_SEA_FLOOR = 4;
geo.ALTITUDE_RELATIVE_TO_SEA_FLOOR = 5;
/*
 * This is an excerpt from the Sylvester linear algebra library, MIT-licensed.
 */
// This file is required in order for any other classes to work. Some Vector methods work with the
// other Sylvester classes and are useless unless they are included. Other classes such as Line and
// Plane will not function at all without Vector being loaded first.

var Sylvester = {
  precision: 1e-6
};

function Vector() {}
Vector.prototype = {

  // Returns element i of the vector
  e: function(i) {
    return (i < 1 || i > this.elements.length) ? null : this.elements[i-1];
  },

  // Returns the number of elements the vector has
  dimensions: function() {
    return this.elements.length;
  },

  // Returns the modulus ('length') of the vector
  modulus: function() {
    return Math.sqrt(this.dot(this));
  },

  // Returns true iff the vector is equal to the argument
  eql: function(vector) {
    var n = this.elements.length;
    var V = vector.elements || vector;
    if (n != V.length) { return false; }
    while (n--) {
      if (Math.abs(this.elements[n] - V[n]) > Sylvester.precision) { return false; }
    }
    return true;
  },

  // Returns a copy of the vector
  dup: function() {
    return Vector.create(this.elements);
  },

  // Maps the vector to another vector according to the given function
  map: function(fn) {
    var elements = [];
    this.each(function(x, i) {
      elements.push(fn(x, i));
    });
    return Vector.create(elements);
  },

  // Calls the iterator for each element of the vector in turn
  each: function(fn) {
    var n = this.elements.length;
    for (var i = 0; i < n; i++) {
      fn(this.elements[i], i+1);
    }
  },

  // Returns a new vector created by normalizing the receiver
  toUnitVector: function() {
    var r = this.modulus();
    if (r === 0) { return this.dup(); }
    return this.map(function(x) { return x/r; });
  },

  // Returns the angle between the vector and the argument (also a vector)
  angleFrom: function(vector) {
    var V = vector.elements || vector;
    var n = this.elements.length, k = n, i;
    if (n != V.length) { return null; }
    var dot = 0, mod1 = 0, mod2 = 0;
    // Work things out in parallel to save time
    this.each(function(x, i) {
      dot += x * V[i-1];
      mod1 += x * x;
      mod2 += V[i-1] * V[i-1];
    });
    mod1 = Math.sqrt(mod1); mod2 = Math.sqrt(mod2);
    if (mod1*mod2 === 0) { return null; }
    var theta = dot / (mod1*mod2);
    if (theta < -1) { theta = -1; }
    if (theta > 1) { theta = 1; }
    return Math.acos(theta);
  },

  // Returns true iff the vector is parallel to the argument
  isParallelTo: function(vector) {
    var angle = this.angleFrom(vector);
    return (angle === null) ? null : (angle <= Sylvester.precision);
  },

  // Returns true iff the vector is antiparallel to the argument
  isAntiparallelTo: function(vector) {
    var angle = this.angleFrom(vector);
    return (angle === null) ? null : (Math.abs(angle - Math.PI) <= Sylvester.precision);
  },

  // Returns true iff the vector is perpendicular to the argument
  isPerpendicularTo: function(vector) {
    var dot = this.dot(vector);
    return (dot === null) ? null : (Math.abs(dot) <= Sylvester.precision);
  },

  // Returns the result of adding the argument to the vector
  add: function(vector) {
    var V = vector.elements || vector;
    if (this.elements.length != V.length) { return null; }
    return this.map(function(x, i) { return x + V[i-1]; });
  },

  // Returns the result of subtracting the argument from the vector
  subtract: function(vector) {
    var V = vector.elements || vector;
    if (this.elements.length != V.length) { return null; }
    return this.map(function(x, i) { return x - V[i-1]; });
  },

  // Returns the result of multiplying the elements of the vector by the argument
  multiply: function(k) {
    return this.map(function(x) { return x*k; });
  },

  x: function(k) { return this.multiply(k); },

  // Returns the scalar product of the vector with the argument
  // Both vectors must have equal dimensionality
  dot: function(vector) {
    var V = vector.elements || vector;
    var i, product = 0, n = this.elements.length;
    if (n != V.length) { return null; }
    while (n--) { product += this.elements[n] * V[n]; }
    return product;
  },

  // Returns the vector product of the vector with the argument
  // Both vectors must have dimensionality 3
  cross: function(vector) {
    var B = vector.elements || vector;
    if (this.elements.length != 3 || B.length != 3) { return null; }
    var A = this.elements;
    return Vector.create([
      (A[1] * B[2]) - (A[2] * B[1]),
      (A[2] * B[0]) - (A[0] * B[2]),
      (A[0] * B[1]) - (A[1] * B[0])
    ]);
  },

  // Returns the (absolute) largest element of the vector
  max: function() {
    var m = 0, i = this.elements.length;
    while (i--) {
      if (Math.abs(this.elements[i]) > Math.abs(m)) { m = this.elements[i]; }
    }
    return m;
  },

  // Returns the index of the first match found
  indexOf: function(x) {
    var index = null, n = this.elements.length;
    for (var i = 0; i < n; i++) {
      if (index === null && this.elements[i] == x) {
        index = i + 1;
      }
    }
    return index;
  },

  // Returns a diagonal matrix with the vector's elements as its diagonal elements
  toDiagonalMatrix: function() {
    return Matrix.Diagonal(this.elements);
  },

  // Returns the result of rounding the elements of the vector
  round: function() {
    return this.map(function(x) { return Math.round(x); });
  },

  // Returns a copy of the vector with elements set to the given value if they
  // differ from it by less than Sylvester.precision
  snapTo: function(x) {
    return this.map(function(y) {
      return (Math.abs(y - x) <= Sylvester.precision) ? x : y;
    });
  },

  // Returns the vector's distance from the argument, when considered as a point in space
  distanceFrom: function(obj) {
    if (obj.anchor || (obj.start && obj.end)) { return obj.distanceFrom(this); }
    var V = obj.elements || obj;
    if (V.length != this.elements.length) { return null; }
    var sum = 0, part;
    this.each(function(x, i) {
      part = x - V[i-1];
      sum += part * part;
    });
    return Math.sqrt(sum);
  },

  // Returns true if the vector is point on the given line
  liesOn: function(line) {
    return line.contains(this);
  },

  // Return true iff the vector is a point in the given plane
  liesIn: function(plane) {
    return plane.contains(this);
  },

  // Rotates the vector about the given object. The object should be a 
  // point if the vector is 2D, and a line if it is 3D. Be careful with line directions!
  rotate: function(t, obj) {
    var V, R = null, x, y, z;
    if (t.determinant) { R = t.elements; }
    switch (this.elements.length) {
      case 2:
        V = obj.elements || obj;
        if (V.length != 2) { return null; }
        if (!R) { R = Matrix.Rotation(t).elements; }
        x = this.elements[0] - V[0];
        y = this.elements[1] - V[1];
        return Vector.create([
          V[0] + R[0][0] * x + R[0][1] * y,
          V[1] + R[1][0] * x + R[1][1] * y
        ]);
        break;
      case 3:
        if (!obj.direction) { return null; }
        var C = obj.pointClosestTo(this).elements;
        if (!R) { R = Matrix.Rotation(t, obj.direction).elements; }
        x = this.elements[0] - C[0];
        y = this.elements[1] - C[1];
        z = this.elements[2] - C[2];
        return Vector.create([
          C[0] + R[0][0] * x + R[0][1] * y + R[0][2] * z,
          C[1] + R[1][0] * x + R[1][1] * y + R[1][2] * z,
          C[2] + R[2][0] * x + R[2][1] * y + R[2][2] * z
        ]);
        break;
      default:
        return null;
    }
  },

  // Returns the result of reflecting the point in the given point, line or plane
  reflectionIn: function(obj) {
    if (obj.anchor) {
      // obj is a plane or line
      var P = this.elements.slice();
      var C = obj.pointClosestTo(P).elements;
      return Vector.create([C[0] + (C[0] - P[0]), C[1] + (C[1] - P[1]), C[2] + (C[2] - (P[2] || 0))]);
    } else {
      // obj is a point
      var Q = obj.elements || obj;
      if (this.elements.length != Q.length) { return null; }
      return this.map(function(x, i) { return Q[i-1] + (Q[i-1] - x); });
    }
  },

  // Utility to make sure vectors are 3D. If they are 2D, a zero z-component is added
  to3D: function() {
    var V = this.dup();
    switch (V.elements.length) {
      case 3: break;
      case 2: V.elements.push(0); break;
      default: return null;
    }
    return V;
  },

  // Returns a string representation of the vector
  inspect: function() {
    return '[' + this.elements.join(', ') + ']';
  },

  // Set vector's elements from an array
  setElements: function(els) {
    this.elements = (els.elements || els).slice();
    return this;
  }
};

// Constructor function
Vector.create = function(elements) {
  var V = new Vector();
  return V.setElements(elements);
};
var $V = Vector.create;

// i, j, k unit vectors
Vector.i = Vector.create([1,0,0]);
Vector.j = Vector.create([0,1,0]);
Vector.k = Vector.create([0,0,1]);

// Random vector of size n
Vector.Random = function(n) {
  var elements = [];
  while (n--) { elements.push(Math.random()); }
  return Vector.create(elements);
};

// Vector filled with zeros
Vector.Zero = function(n) {
  var elements = [];
  while (n--) { elements.push(0); }
  return Vector.create(elements);
};// Matrix class - depends on Vector.

function Matrix() {}
Matrix.prototype = {

  // Returns element (i,j) of the matrix
  e: function(i,j) {
    if (i < 1 || i > this.elements.length || j < 1 || j > this.elements[0].length) { return null; }
    return this.elements[i-1][j-1];
  },

  // Returns row k of the matrix as a vector
  row: function(i) {
    if (i > this.elements.length) { return null; }
    return Vector.create(this.elements[i-1]);
  },

  // Returns column k of the matrix as a vector
  col: function(j) {
    if (j > this.elements[0].length) { return null; }
    var col = [], n = this.elements.length;
    for (var i = 0; i < n; i++) { col.push(this.elements[i][j-1]); }
    return Vector.create(col);
  },

  // Returns the number of rows/columns the matrix has
  dimensions: function() {
    return {rows: this.elements.length, cols: this.elements[0].length};
  },

  // Returns the number of rows in the matrix
  rows: function() {
    return this.elements.length;
  },

  // Returns the number of columns in the matrix
  cols: function() {
    return this.elements[0].length;
  },

  // Returns true iff the matrix is equal to the argument. You can supply
  // a vector as the argument, in which case the receiver must be a
  // one-column matrix equal to the vector.
  eql: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    if (this.elements.length != M.length ||
        this.elements[0].length != M[0].length) { return false; }
    var i = this.elements.length, nj = this.elements[0].length, j;
    while (i--) { j = nj;
      while (j--) {
        if (Math.abs(this.elements[i][j] - M[i][j]) > Sylvester.precision) { return false; }
      }
    }
    return true;
  },

  // Returns a copy of the matrix
  dup: function() {
    return Matrix.create(this.elements);
  },

  // Maps the matrix to another matrix (of the same dimensions) according to the given function
  map: function(fn) {
    var els = [], i = this.elements.length, nj = this.elements[0].length, j;
    while (i--) { j = nj;
      els[i] = [];
      while (j--) {
        els[i][j] = fn(this.elements[i][j], i + 1, j + 1);
      }
    }
    return Matrix.create(els);
  },

  // Returns true iff the argument has the same dimensions as the matrix
  isSameSizeAs: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    return (this.elements.length == M.length &&
        this.elements[0].length == M[0].length);
  },

  // Returns the result of adding the argument to the matrix
  add: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    if (!this.isSameSizeAs(M)) { return null; }
    return this.map(function(x, i, j) { return x + M[i-1][j-1]; });
  },

  // Returns the result of subtracting the argument from the matrix
  subtract: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    if (!this.isSameSizeAs(M)) { return null; }
    return this.map(function(x, i, j) { return x - M[i-1][j-1]; });
  },

  // Returns true iff the matrix can multiply the argument from the left
  canMultiplyFromLeft: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    // this.columns should equal matrix.rows
    return (this.elements[0].length == M.length);
  },

  // Returns the result of multiplying the matrix from the right by the argument.
  // If the argument is a scalar then just multiply all the elements. If the argument is
  // a vector, a vector is returned, which saves you having to remember calling
  // col(1) on the result.
  multiply: function(matrix) {
    if (!matrix.elements) {
      return this.map(function(x) { return x * matrix; });
    }
    var returnVector = matrix.modulus ? true : false;
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    if (!this.canMultiplyFromLeft(M)) { return null; }
    var i = this.elements.length, nj = M[0].length, j;
    var cols = this.elements[0].length, c, elements = [], sum;
    while (i--) { j = nj;
      elements[i] = [];
      while (j--) { c = cols;
        sum = 0;
        while (c--) {
          sum += this.elements[i][c] * M[c][j];
        }
        elements[i][j] = sum;
      }
    }
    var M = Matrix.create(elements);
    return returnVector ? M.col(1) : M;
  },

  x: function(matrix) { return this.multiply(matrix); },

  // Returns a submatrix taken from the matrix
  // Argument order is: start row, start col, nrows, ncols
  // Element selection wraps if the required index is outside the matrix's bounds, so you could
  // use this to perform row/column cycling or copy-augmenting.
  minor: function(a, b, c, d) {
    var elements = [], ni = c, i, nj, j;
    var rows = this.elements.length, cols = this.elements[0].length;
    while (ni--) { i = c - ni - 1;
      elements[i] = [];
      nj = d;
      while (nj--) { j = d - nj - 1;
        elements[i][j] = this.elements[(a+i-1)%rows][(b+j-1)%cols];
      }
    }
    return Matrix.create(elements);
  },

  // Returns the transpose of the matrix
  transpose: function() {
    var rows = this.elements.length, i, cols = this.elements[0].length, j;
    var elements = [], i = cols;
    while (i--) { j = rows;
      elements[i] = [];
      while (j--) {
        elements[i][j] = this.elements[j][i];
      }
    }
    return Matrix.create(elements);
  },

  // Returns true iff the matrix is square
  isSquare: function() {
    return (this.elements.length == this.elements[0].length);
  },

  // Returns the (absolute) largest element of the matrix
  max: function() {
    var m = 0, i = this.elements.length, nj = this.elements[0].length, j;
    while (i--) { j = nj;
      while (j--) {
        if (Math.abs(this.elements[i][j]) > Math.abs(m)) { m = this.elements[i][j]; }
      }
    }
    return m;
  },

  // Returns the indeces of the first match found by reading row-by-row from left to right
  indexOf: function(x) {
    var index = null, ni = this.elements.length, i, nj = this.elements[0].length, j;
    for (i = 0; i < ni; i++) {
      for (j = 0; j < nj; j++) {
        if (this.elements[i][j] == x) { return {i: i+1, j: j+1}; }
      }
    }
    return null;
  },

  // If the matrix is square, returns the diagonal elements as a vector.
  // Otherwise, returns null.
  diagonal: function() {
    if (!this.isSquare) { return null; }
    var els = [], n = this.elements.length;
    for (var i = 0; i < n; i++) {
      els.push(this.elements[i][i]);
    }
    return Vector.create(els);
  },

  // Make the matrix upper (right) triangular by Gaussian elimination.
  // This method only adds multiples of rows to other rows. No rows are
  // scaled up or switched, and the determinant is preserved.
  toRightTriangular: function() {
    var M = this.dup(), els;
    var n = this.elements.length, i, j, np = this.elements[0].length, p;
    for (i = 0; i < n; i++) {
      if (M.elements[i][i] == 0) {
        for (j = i + 1; j < n; j++) {
          if (M.elements[j][i] != 0) {
            els = [];
            for (p = 0; p < np; p++) { els.push(M.elements[i][p] + M.elements[j][p]); }
            M.elements[i] = els;
            break;
          }
        }
      }
      if (M.elements[i][i] != 0) {
        for (j = i + 1; j < n; j++) {
          var multiplier = M.elements[j][i] / M.elements[i][i];
          els = [];
          for (p = 0; p < np; p++) {
            // Elements with column numbers up to an including the number
            // of the row that we're subtracting can safely be set straight to
            // zero, since that's the point of this routine and it avoids having
            // to loop over and correct rounding errors later
            els.push(p <= i ? 0 : M.elements[j][p] - M.elements[i][p] * multiplier);
          }
          M.elements[j] = els;
        }
      }
    }
    return M;
  },

  toUpperTriangular: function() { return this.toRightTriangular(); },

  // Returns the determinant for square matrices
  determinant: function() {
    if (!this.isSquare()) { return null; }
    var M = this.toRightTriangular();
    var det = M.elements[0][0], n = M.elements.length;
    for (var i = 1; i < n; i++) {
      det = det * M.elements[i][i];
    }
    return det;
  },

  det: function() { return this.determinant(); },

  // Returns true iff the matrix is singular
  isSingular: function() {
    return (this.isSquare() && this.determinant() === 0);
  },

  // Returns the trace for square matrices
  trace: function() {
    if (!this.isSquare()) { return null; }
    var tr = this.elements[0][0], n = this.elements.length;
    for (var i = 1; i < n; i++) {
      tr += this.elements[i][i];
    }
    return tr;
  },

  tr: function() { return this.trace(); },

  // Returns the rank of the matrix
  rank: function() {
    var M = this.toRightTriangular(), rank = 0;
    var i = this.elements.length, nj = this.elements[0].length, j;
    while (i--) { j = nj;
      while (j--) {
        if (Math.abs(M.elements[i][j]) > Sylvester.precision) { rank++; break; }
      }
    }
    return rank;
  },

  rk: function() { return this.rank(); },

  // Returns the result of attaching the given argument to the right-hand side of the matrix
  augment: function(matrix) {
    var M = matrix.elements || matrix;
    if (typeof(M[0][0]) == 'undefined') { M = Matrix.create(M).elements; }
    var T = this.dup(), cols = T.elements[0].length;
    var i = T.elements.length, nj = M[0].length, j;
    if (i != M.length) { return null; }
    while (i--) { j = nj;
      while (j--) {
        T.elements[i][cols + j] = M[i][j];
      }
    }
    return T;
  },

  // Returns the inverse (if one exists) using Gauss-Jordan
  inverse: function() {
    if (!this.isSquare() || this.isSingular()) { return null; }
    var n = this.elements.length, i= n, j;
    var M = this.augment(Matrix.I(n)).toRightTriangular();
    var np = M.elements[0].length, p, els, divisor;
    var inverse_elements = [], new_element;
    // Matrix is non-singular so there will be no zeros on the diagonal
    // Cycle through rows from last to first
    while (i--) {
      // First, normalise diagonal elements to 1
      els = [];
      inverse_elements[i] = [];
      divisor = M.elements[i][i];
      for (p = 0; p < np; p++) {
        new_element = M.elements[i][p] / divisor;
        els.push(new_element);
        // Shuffle off the current row of the right hand side into the results
        // array as it will not be modified by later runs through this loop
        if (p >= n) { inverse_elements[i].push(new_element); }
      }
      M.elements[i] = els;
      // Then, subtract this row from those above it to
      // give the identity matrix on the left hand side
      j = i;
      while (j--) {
        els = [];
        for (p = 0; p < np; p++) {
          els.push(M.elements[j][p] - M.elements[i][p] * M.elements[j][i]);
        }
        M.elements[j] = els;
      }
    }
    return Matrix.create(inverse_elements);
  },

  inv: function() { return this.inverse(); },

  // Returns the result of rounding all the elements
  round: function() {
    return this.map(function(x) { return Math.round(x); });
  },

  // Returns a copy of the matrix with elements set to the given value if they
  // differ from it by less than Sylvester.precision
  snapTo: function(x) {
    return this.map(function(p) {
      return (Math.abs(p - x) <= Sylvester.precision) ? x : p;
    });
  },

  // Returns a string representation of the matrix
  inspect: function() {
    var matrix_rows = [];
    var n = this.elements.length;
    for (var i = 0; i < n; i++) {
      matrix_rows.push(Vector.create(this.elements[i]).inspect());
    }
    return matrix_rows.join('\n');
  },

  // Set the matrix's elements from an array. If the argument passed
  // is a vector, the resulting matrix will be a single column.
  setElements: function(els) {
    var i, j, elements = els.elements || els;
    if (typeof(elements[0][0]) != 'undefined') {
      i = elements.length;
      this.elements = [];
      while (i--) { j = elements[i].length;
        this.elements[i] = [];
        while (j--) {
          this.elements[i][j] = elements[i][j];
        }
      }
      return this;
    }
    var n = elements.length;
    this.elements = [];
    for (i = 0; i < n; i++) {
      this.elements.push([elements[i]]);
    }
    return this;
  }
};

// Constructor function
Matrix.create = function(elements) {
  var M = new Matrix();
  return M.setElements(elements);
};
var $M = Matrix.create;

// Identity matrix of size n
Matrix.I = function(n) {
  var els = [], i = n, j;
  while (i--) { j = n;
    els[i] = [];
    while (j--) {
      els[i][j] = (i == j) ? 1 : 0;
    }
  }
  return Matrix.create(els);
};

// Diagonal matrix - all off-diagonal elements are zero
Matrix.Diagonal = function(elements) {
  var i = elements.length;
  var M = Matrix.I(i);
  while (i--) {
    M.elements[i][i] = elements[i];
  }
  return M;
};

// Rotation matrix about some axis. If no axis is
// supplied, assume we're after a 2D transform
Matrix.Rotation = function(theta, a) {
  if (!a) {
    return Matrix.create([
      [Math.cos(theta),  -Math.sin(theta)],
      [Math.sin(theta),   Math.cos(theta)]
    ]);
  }
  var axis = a.dup();
  if (axis.elements.length != 3) { return null; }
  var mod = axis.modulus();
  var x = axis.elements[0]/mod, y = axis.elements[1]/mod, z = axis.elements[2]/mod;
  var s = Math.sin(theta), c = Math.cos(theta), t = 1 - c;
  // Formula derived here: http://www.gamedev.net/reference/articles/article1199.asp
  // That proof rotates the co-ordinate system so theta
  // becomes -theta and sin becomes -sin here.
  return Matrix.create([
    [ t*x*x + c, t*x*y - s*z, t*x*z + s*y ],
    [ t*x*y + s*z, t*y*y + c, t*y*z - s*x ],
    [ t*x*z - s*y, t*y*z + s*x, t*z*z + c ]
  ]);
};

// Special case rotations
Matrix.RotationX = function(t) {
  var c = Math.cos(t), s = Math.sin(t);
  return Matrix.create([
    [  1,  0,  0 ],
    [  0,  c, -s ],
    [  0,  s,  c ]
  ]);
};
Matrix.RotationY = function(t) {
  var c = Math.cos(t), s = Math.sin(t);
  return Matrix.create([
    [  c,  0,  s ],
    [  0,  1,  0 ],
    [ -s,  0,  c ]
  ]);
};
Matrix.RotationZ = function(t) {
  var c = Math.cos(t), s = Math.sin(t);
  return Matrix.create([
    [  c, -s,  0 ],
    [  s,  c,  0 ],
    [  0,  0,  1 ]
  ]);
};

// Random matrix of n rows, m columns
Matrix.Random = function(n, m) {
  return Matrix.Zero(n, m).map(
    function() { return Math.random(); }
  );
};

// Matrix filled with zeros
Matrix.Zero = function(n, m) {
  var els = [], i = n, j;
  while (i--) { j = m;
    els[i] = [];
    while (j--) {
      els[i][j] = 0;
    }
  }
  return Matrix.create(els);
};// Line class - depends on Vector, and some methods require Matrix and Plane.

function Line() {}
Line.prototype = {

  // Returns true if the argument occupies the same space as the line
  eql: function(line) {
    return (this.isParallelTo(line) && this.contains(line.anchor));
  },

  // Returns a copy of the line
  dup: function() {
    return Line.create(this.anchor, this.direction);
  },

  // Returns the result of translating the line by the given vector/array
  translate: function(vector) {
    var V = vector.elements || vector;
    return Line.create([
      this.anchor.elements[0] + V[0],
      this.anchor.elements[1] + V[1],
      this.anchor.elements[2] + (V[2] || 0)
    ], this.direction);
  },

  // Returns true if the line is parallel to the argument. Here, 'parallel to'
  // means that the argument's direction is either parallel or antiparallel to
  // the line's own direction. A line is parallel to a plane if the two do not
  // have a unique intersection.
  isParallelTo: function(obj) {
    if (obj.normal || (obj.start && obj.end)) { return obj.isParallelTo(this); }
    var theta = this.direction.angleFrom(obj.direction);
    return (Math.abs(theta) <= Sylvester.precision || Math.abs(theta - Math.PI) <= Sylvester.precision);
  },

  // Returns the line's perpendicular distance from the argument,
  // which can be a point, a line or a plane
  distanceFrom: function(obj) {
    if (obj.normal || (obj.start && obj.end)) { return obj.distanceFrom(this); }
    if (obj.direction) {
      // obj is a line
      if (this.isParallelTo(obj)) { return this.distanceFrom(obj.anchor); }
      var N = this.direction.cross(obj.direction).toUnitVector().elements;
      var A = this.anchor.elements, B = obj.anchor.elements;
      return Math.abs((A[0] - B[0]) * N[0] + (A[1] - B[1]) * N[1] + (A[2] - B[2]) * N[2]);
    } else {
      // obj is a point
      var P = obj.elements || obj;
      var A = this.anchor.elements, D = this.direction.elements;
      var PA1 = P[0] - A[0], PA2 = P[1] - A[1], PA3 = (P[2] || 0) - A[2];
      var modPA = Math.sqrt(PA1*PA1 + PA2*PA2 + PA3*PA3);
      if (modPA === 0) return 0;
      // Assumes direction vector is normalized
      var cosTheta = (PA1 * D[0] + PA2 * D[1] + PA3 * D[2]) / modPA;
      var sin2 = 1 - cosTheta*cosTheta;
      return Math.abs(modPA * Math.sqrt(sin2 < 0 ? 0 : sin2));
    }
  },

  // Returns true iff the argument is a point on the line, or if the argument
  // is a line segment lying within the receiver
  contains: function(obj) {
    if (obj.start && obj.end) { return this.contains(obj.start) && this.contains(obj.end); }
    var dist = this.distanceFrom(obj);
    return (dist !== null && dist <= Sylvester.precision);
  },

  // Returns the distance from the anchor of the given point. Negative values are
  // returned for points that are in the opposite direction to the line's direction from
  // the line's anchor point.
  positionOf: function(point) {
    if (!this.contains(point)) { return null; }
    var P = point.elements || point;
    var A = this.anchor.elements, D = this.direction.elements;
    return (P[0] - A[0]) * D[0] + (P[1] - A[1]) * D[1] + ((P[2] || 0) - A[2]) * D[2];
  },

  // Returns true iff the line lies in the given plane
  liesIn: function(plane) {
    return plane.contains(this);
  },

  // Returns true iff the line has a unique point of intersection with the argument
  intersects: function(obj) {
    if (obj.normal) { return obj.intersects(this); }
    return (!this.isParallelTo(obj) && this.distanceFrom(obj) <= Sylvester.precision);
  },

  // Returns the unique intersection point with the argument, if one exists
  intersectionWith: function(obj) {
    if (obj.normal || (obj.start && obj.end)) { return obj.intersectionWith(this); }
    if (!this.intersects(obj)) { return null; }
    var P = this.anchor.elements, X = this.direction.elements,
        Q = obj.anchor.elements, Y = obj.direction.elements;
    var X1 = X[0], X2 = X[1], X3 = X[2], Y1 = Y[0], Y2 = Y[1], Y3 = Y[2];
    var PsubQ1 = P[0] - Q[0], PsubQ2 = P[1] - Q[1], PsubQ3 = P[2] - Q[2];
    var XdotQsubP = - X1*PsubQ1 - X2*PsubQ2 - X3*PsubQ3;
    var YdotPsubQ = Y1*PsubQ1 + Y2*PsubQ2 + Y3*PsubQ3;
    var XdotX = X1*X1 + X2*X2 + X3*X3;
    var YdotY = Y1*Y1 + Y2*Y2 + Y3*Y3;
    var XdotY = X1*Y1 + X2*Y2 + X3*Y3;
    var k = (XdotQsubP * YdotY / XdotX + XdotY * YdotPsubQ) / (YdotY - XdotY * XdotY);
    return Vector.create([P[0] + k*X1, P[1] + k*X2, P[2] + k*X3]);
  },

  // Returns the point on the line that is closest to the given point or line/line segment
  pointClosestTo: function(obj) {
    if (obj.start && obj.end) {
      // obj is a line segment
      var P = obj.pointClosestTo(this);
      return (P === null) ? null : this.pointClosestTo(P);
    } else if (obj.direction) {
      // obj is a line
      if (this.intersects(obj)) { return this.intersectionWith(obj); }
      if (this.isParallelTo(obj)) { return null; }
      var D = this.direction.elements, E = obj.direction.elements;
      var D1 = D[0], D2 = D[1], D3 = D[2], E1 = E[0], E2 = E[1], E3 = E[2];
      // Create plane containing obj and the shared normal and intersect this with it
      // Thank you: http://www.cgafaq.info/wiki/Line-line_distance
      var x = (D3 * E1 - D1 * E3), y = (D1 * E2 - D2 * E1), z = (D2 * E3 - D3 * E2);
      var N = [x * E3 - y * E2, y * E1 - z * E3, z * E2 - x * E1];
      var P = Plane.create(obj.anchor, N);
      return P.intersectionWith(this);
    } else {
      // obj is a point
      var P = obj.elements || obj;
      if (this.contains(P)) { return Vector.create(P); }
      var A = this.anchor.elements, D = this.direction.elements;
      var D1 = D[0], D2 = D[1], D3 = D[2], A1 = A[0], A2 = A[1], A3 = A[2];
      var x = D1 * (P[1]-A2) - D2 * (P[0]-A1), y = D2 * ((P[2] || 0) - A3) - D3 * (P[1]-A2),
          z = D3 * (P[0]-A1) - D1 * ((P[2] || 0) - A3);
      var V = Vector.create([D2 * x - D3 * z, D3 * y - D1 * x, D1 * z - D2 * y]);
      var k = this.distanceFrom(P) / V.modulus();
      return Vector.create([
        P[0] + V.elements[0] * k,
        P[1] + V.elements[1] * k,
        (P[2] || 0) + V.elements[2] * k
      ]);
    }
  },

  // Returns a copy of the line rotated by t radians about the given line. Works by
  // finding the argument's closest point to this line's anchor point (call this C) and
  // rotating the anchor about C. Also rotates the line's direction about the argument's.
  // Be careful with this - the rotation axis' direction affects the outcome!
  rotate: function(t, line) {
    // If we're working in 2D
    if (typeof(line.direction) == 'undefined') { line = Line.create(line.to3D(), Vector.k); }
    var R = Matrix.Rotation(t, line.direction).elements;
    var C = line.pointClosestTo(this.anchor).elements;
    var A = this.anchor.elements, D = this.direction.elements;
    var C1 = C[0], C2 = C[1], C3 = C[2], A1 = A[0], A2 = A[1], A3 = A[2];
    var x = A1 - C1, y = A2 - C2, z = A3 - C3;
    return Line.create([
      C1 + R[0][0] * x + R[0][1] * y + R[0][2] * z,
      C2 + R[1][0] * x + R[1][1] * y + R[1][2] * z,
      C3 + R[2][0] * x + R[2][1] * y + R[2][2] * z
    ], [
      R[0][0] * D[0] + R[0][1] * D[1] + R[0][2] * D[2],
      R[1][0] * D[0] + R[1][1] * D[1] + R[1][2] * D[2],
      R[2][0] * D[0] + R[2][1] * D[1] + R[2][2] * D[2]
    ]);
  },

  // Returns a copy of the line with its direction vector reversed.
  // Useful when using lines for rotations.
  reverse: function() {
    return Line.create(this.anchor, this.direction.x(-1));
  },

  // Returns the line's reflection in the given point or line
  reflectionIn: function(obj) {
    if (obj.normal) {
      // obj is a plane
      var A = this.anchor.elements, D = this.direction.elements;
      var A1 = A[0], A2 = A[1], A3 = A[2], D1 = D[0], D2 = D[1], D3 = D[2];
      var newA = this.anchor.reflectionIn(obj).elements;
      // Add the line's direction vector to its anchor, then mirror that in the plane
      var AD1 = A1 + D1, AD2 = A2 + D2, AD3 = A3 + D3;
      var Q = obj.pointClosestTo([AD1, AD2, AD3]).elements;
      var newD = [Q[0] + (Q[0] - AD1) - newA[0], Q[1] + (Q[1] - AD2) - newA[1], Q[2] + (Q[2] - AD3) - newA[2]];
      return Line.create(newA, newD);
    } else if (obj.direction) {
      // obj is a line - reflection obtained by rotating PI radians about obj
      return this.rotate(Math.PI, obj);
    } else {
      // obj is a point - just reflect the line's anchor in it
      var P = obj.elements || obj;
      return Line.create(this.anchor.reflectionIn([P[0], P[1], (P[2] || 0)]), this.direction);
    }
  },

  // Set the line's anchor point and direction.
  setVectors: function(anchor, direction) {
    // Need to do this so that line's properties are not
    // references to the arguments passed in
    anchor = Vector.create(anchor);
    direction = Vector.create(direction);
    if (anchor.elements.length == 2) {anchor.elements.push(0); }
    if (direction.elements.length == 2) { direction.elements.push(0); }
    if (anchor.elements.length > 3 || direction.elements.length > 3) { return null; }
    var mod = direction.modulus();
    if (mod === 0) { return null; }
    this.anchor = anchor;
    this.direction = Vector.create([
      direction.elements[0] / mod,
      direction.elements[1] / mod,
      direction.elements[2] / mod
    ]);
    return this;
  }
};

// Constructor function
Line.create = function(anchor, direction) {
  var L = new Line();
  return L.setVectors(anchor, direction);
};
var $L = Line.create;

// Axes
Line.X = Line.create(Vector.Zero(3), Vector.i);
Line.Y = Line.create(Vector.Zero(3), Vector.j);
Line.Z = Line.create(Vector.Zero(3), Vector.k);/**
 * @namespace
 */
geo.linalg = {};

geo.linalg.Vector = function() {
  return Vector.create.apply(null, arguments);
};
geo.linalg.Vector.create = Vector.create;
geo.linalg.Vector.i = Vector.i;
geo.linalg.Vector.j = Vector.j;
geo.linalg.Vector.k = Vector.k;
geo.linalg.Vector.Random = Vector.Random;
geo.linalg.Vector.Zero = Vector.Zero;

geo.linalg.Matrix = function() {
  return Matrix.create.apply(null, arguments);
};
geo.linalg.Matrix.create = Matrix.create;
geo.linalg.Matrix.I = Matrix.I;
geo.linalg.Matrix.Random = Matrix.Random;
geo.linalg.Matrix.Rotation = Matrix.Rotation;
geo.linalg.Matrix.RotationX = Matrix.RotationX;
geo.linalg.Matrix.RotationY = Matrix.RotationY;
geo.linalg.Matrix.RotationZ = Matrix.RotationZ;
geo.linalg.Matrix.Zero = Matrix.Zero;

geo.linalg.Line = function() {
  return Line.create.apply(null, arguments);
};
geo.linalg.Line.create = Line.create;
geo.linalg.Line.X = Line.X;
geo.linalg.Line.Y = Line.Y;
geo.linalg.Line.Z = Line.Z;
/**
 * @namespace
 */
geo.math = {isnamespace_:true};
/**
 * Converts an angle from radians to degrees.
 * @type Number
 * @return Returns the angle, converted to degrees.
 */
if (!('toDegrees' in Number.prototype)) {
  Number.prototype.toDegrees = function() {
    return this * 180 / Math.PI;
  };
}

/**
 * Converts an angle from degrees to radians.
 * @type Number
 * @return Returns the angle, converted to radians.
 */
if (!('toRadians' in Number.prototype)) {
  Number.prototype.toRadians = function() {
    return this * Math.PI / 180;
  };
}
/**
 * Normalizes an angle to the [0,2pi) range.
 * @param {Number} angleRad The angle to normalize, in radians.
 * @type Number
 * @return Returns the angle, fit within the [0,2pi) range, in radians.
 */
geo.math.normalizeAngle = function(angleRad) {
  angleRad = angleRad % (2 * Math.PI);
  return angleRad >= 0 ? angleRad : angleRad + 2 * Math.PI;
};

/**
 * Normalizes a latitude to the [-90,90] range. Latitudes above 90 or
 * below -90 are capped, not wrapped.
 * @param {Number} lat The latitude to normalize, in degrees.
 * @type Number
 * @return Returns the latitude, fit within the [-90,90] range.
 */
geo.math.normalizeLat = function(lat) {
  return Math.max(-90, Math.min(90, lat));
};

/**
 * Normalizes a longitude to the [-180,180] range. Longitudes above 180
 * or below -180 are wrapped.
 * @param {Number} lng The longitude to normalize, in degrees.
 * @type Number
 * @return Returns the latitude, fit within the [-90,90] range.
 */
geo.math.normalizeLng = function(lng) {
  if (lng % 360 == 180) {
    return 180;
  }

  lng = lng % 360;
  return lng < -180 ? lng + 360 : lng > 180 ? lng - 360 : lng;
};

/**
 * Reverses an angle.
 * @param {Number} angleRad The angle to reverse, in radians.
 * @type Number
 * @return Returns the reverse angle, in radians.
 */
geo.math.reverseAngle = function(angleRad) {
  return geo.math.normalizeAngle(angleRad + Math.PI);
};

/**
 * Wraps the given number to the given range. If the wrapped value is exactly
 * equal to min or max, favors max, unless favorMin is true.
 * @param {Number} value The value to wrap.
 * @param {Number[]} range An array of two numbers, specifying the minimum and
 *     maximum bounds of the range, respectively.
 * @param {Boolean} [favorMin=false] Whether or not to favor min over
 *     max in the case of ambiguity.
 * @return {Number} Returns the value wrapped to the given range.
 */
geo.math.wrapValue = function(value, range, favorMin) {
  if (!range || !geo.util.isArray(range) || range.length != 2) {
    throw new TypeError('The range parameter must be an array of 2 numbers.');
  }
  
  // Don't wrap min as max.
  if (value === range[0]) {
    return range[0];
  }
  
  // Normalize to min = 0.
  value -= range[0];
  
  value = value % (range[1] - range[0]);
  if (value < 0) {
    value += (range[1] - range[0]);
  }
  
  // Reverse normalization.
  value += range[0];
  
  // When ambiguous (min or max), return max unless favorMin is true.
  return (value === range[0]) ? (favorMin ? range[0] : range[1]) : value;
};

/**
 * Constrains the given number to the given range.
 * @param {Number} value The value to constrain.
 * @param {Number[]} range An array of two numbers, specifying the minimum and
 *     maximum bounds of the range, respectively.
 * @return {Number} Returns the value constrained to the given range.
 */
geo.math.constrainValue = function(value, range) {
  if (!range || !geo.util.isArray(range) || range.length != 2) {
    throw new TypeError('The range parameter must be an array of 2 numbers.');
  }
  
  return Math.max(range[0], Math.min(range[1], value));
};
/**
 * The radius of the Earth, in meters, assuming the Earth is a perfect sphere.
 * @see http://en.wikipedia.org/wiki/Earth_radius
 * @type Number
 */
geo.math.EARTH_RADIUS = 6378135;

/**
 * The average radius-of-curvature of the Earth, in meters.
 * @see http://en.wikipedia.org/wiki/Radius_of_curvature_(applications)
 * @type Number
 * @ignore
 */
geo.math.EARTH_RADIUS_CURVATURE_AVG = 6372795;
/**
 * Returns the approximate sea level great circle (Earth) distance between
 * two points using the Haversine formula and assuming an Earth radius of
 * geo.math.EARTH_RADIUS.
 * @param {geo.Point} point1 The first point.
 * @param {geo.Point} point2 The second point.
 * @return {Number} The Earth distance between the two points, in meters.
 * @see http://www.movable-type.co.uk/scripts/latlong.html
 */
geo.math.distance = function(point1, point2) {
  return geo.math.EARTH_RADIUS * geo.math.angularDistance(point1, point2);
};

/*
Vincenty formula:
geo.math.angularDistance = function(point1, point2) {
  point1 = new geo.Point(point1);
  point2 = new geo.Point(point2);
  
  var phi1 = point1.lat.toRadians();
  var phi2 = point2.lat.toRadians();
  
  var sin_phi1 = Math.sin(phi1);
  var cos_phi1 = Math.cos(phi1);
  
  var sin_phi2 = Math.sin(phi2);
  var cos_phi2 = Math.cos(phi2);
  
  var sin_d_lmd = Math.sin(
      point2.lng.toRadians() - point1.lng.toRadians());
  var cos_d_lmd = Math.cos(
      point2.lng.toRadians() - point1.lng.toRadians());
  
  // TODO: options to specify formula
  // TODO: compute radius of curvature at given point for more precision
  
  // Vincenty formula (may replace with Haversine for performance?)
  return Math.atan2(
      Math.sqrt(
        Math.pow(cos_phi2 * sin_d_lmd, 2) +
        Math.pow(cos_phi1 * sin_phi2 - sin_phi1 * cos_phi2 * cos_d_lmd, 2)
      ), sin_phi1 * sin_phi2 + cos_phi1 * cos_phi2 * cos_d_lmd);
}
*/
/**
 * Returns the angular distance between two points using the Haversine
 * formula.
 * @see geo.math.distance
 * @ignore
 */
geo.math.angularDistance = function(point1, point2) {
  var phi1 = point1.lat().toRadians();
  var phi2 = point2.lat().toRadians();
  
  var d_phi = (point2.lat() - point1.lat()).toRadians();
  var d_lmd = (point2.lng() - point1.lng()).toRadians();
  
  var A = Math.pow(Math.sin(d_phi / 2), 2) +
          Math.cos(phi1) * Math.cos(phi2) *
            Math.pow(Math.sin(d_lmd / 2), 2);
  
  return 2 * Math.atan2(Math.sqrt(A), Math.sqrt(1 - A));
};
// TODO: add non-sea level distance using Earth API's math3d.js or Sylvester
/*
    p1 = V3.latLonAltToCartesian([loc1.lat(), loc1.lng(),
      this.ge.getGlobe().getGroundAltitude(loc1.lat(), loc1.lng())]);
    p2 = V3.latLonAltToCartesian([loc2.lat(), loc2.lng(),
      this.ge.getGlobe().getGroundAltitude(loc2.lat(), loc2.lng())]);
    return V3.earthDistance(p1, p2);
*/

/**
 * Calculates the initial heading/bearing at which an object at the start
 * point will need to travel to get to the destination point.
 * @param {geo.Point} start The start point.
 * @param {geo.Point} dest The destination point.
 * @return {Number} The initial heading required to get to the destination
 *     point, in the [0,360) degree range.
 * @see http://mathforum.org/library/drmath/view/55417.html
 */
geo.math.heading = function(start, dest) {
  var phi1 = start.lat().toRadians();
  var phi2 = dest.lat().toRadians();
  var cos_phi2 = Math.cos(phi2);
  
  var d_lmd = (dest.lng() - start.lng()).toRadians();
  
  return geo.math.normalizeAngle(Math.atan2(
      Math.sin(d_lmd) * cos_phi2,
      Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * cos_phi2 *
        Math.cos(d_lmd))).toDegrees();
};

/**
 * @function
 * @param {geo.Point} start
 * @param {geo.Point} dest
 * @return {Number}
 * @see geo.math.heading
 */
geo.math.bearing = geo.math.heading;

/**
 * Calculates an intermediate point on the geodesic between the two given
 * points.
 * @param {geo.Point} point1 The first point.
 * @param {geo.Point} point2 The second point.
 * @param {Number} [fraction] The fraction of distance between the first
 *     and second points.
 * @return {geo.Point}
 * @see http://williams.best.vwh.net/avform.htm#Intermediate
 */
geo.math.midpoint = function(point1, point2, fraction) {
  // TODO: check for antipodality and fail w/ exception in that case
  if (geo.util.isUndefined(fraction) || fraction === null) {
    fraction = 0.5;
  }
  
  if (point1.equals(point2)) {
    return new geo.Point(point1);
  }
  
  var phi1 = point1.lat().toRadians();
  var phi2 = point2.lat().toRadians();
  var lmd1 = point1.lng().toRadians();
  var lmd2 = point2.lng().toRadians();
  
  var cos_phi1 = Math.cos(phi1);
  var cos_phi2 = Math.cos(phi2);
  
  var angularDistance = geo.math.angularDistance(point1, point2);
  var sin_angularDistance = Math.sin(angularDistance);
  
  var A = Math.sin((1 - fraction) * angularDistance) / sin_angularDistance;
  var B = Math.sin(fraction * angularDistance) / sin_angularDistance;
  
  var x = A * cos_phi1 * Math.cos(lmd1) +
          B * cos_phi2 * Math.cos(lmd2);
  
  var y = A * cos_phi1 * Math.sin(lmd1) +
          B * cos_phi2 * Math.sin(lmd2);
  
  var z = A * Math.sin(phi1) +
          B * Math.sin(phi2);
  
  return new geo.Point(
      Math.atan2(z, Math.sqrt(Math.pow(x, 2) +
                              Math.pow(y, 2))).toDegrees(),
      Math.atan2(y, x).toDegrees());
};

/**
 * Calculates the destination point along a geodesic, given an initial heading
 * and distance, from the given start point.
 * @see http://www.movable-type.co.uk/scripts/latlong.html
 * @param {geo.Point} start The start point.
 * @param {Object} options The heading and distance object literal.
 * @param {Number} options.heading The initial heading, in degrees.
 * @param {Number} options.distance The distance along the geodesic, in meters.
 * @return {geo.Point}
 */
geo.math.destination = function(start, options) {
  if (!('heading' in options && 'distance' in options)) {
    throw new TypeError('destination() requres both heading and ' +
                        'distance options.');
  }
  
  var phi1 = start.lat().toRadians();
  
  var sin_phi1 = Math.sin(phi1);
  
  var angularDistance = options.distance / geo.math.EARTH_RADIUS;
  var heading_rad = options.heading.toRadians();
  
  var sin_angularDistance = Math.sin(angularDistance);
  var cos_angularDistance = Math.cos(angularDistance);
  
  var phi2 = Math.asin(
               sin_phi1 * cos_angularDistance + 
               Math.cos(phi1) * sin_angularDistance *
                 Math.cos(heading_rad));
  
  return new geo.Point(
      phi2.toDegrees(),
      Math.atan2(
        Math.sin(heading_rad) *
          sin_angularDistance * Math.cos(phi2),
        cos_angularDistance - sin_phi1 * Math.sin(phi2)).toDegrees() +
        start.lng());
};
/**
 * Creates a new point from the given parameters.
 * @param {geo.Point|Number[]|KmlPoint|KmlLookAt|KmlCoord|KmlLocation|GLatLng}
 *     src The point data.
 * @constructor
 */
geo.Point = function() {
  var pointArraySrc = null;
  
  // 1 argument constructor
  if (arguments.length == 1) {
    var point = arguments[0];
    
    // copy constructor
    if (point.constructor === geo.Point) {
      this.lat_ = point.lat();
      this.lng_ = point.lng();
      this.altitude_ = point.altitude();
      this.altitudeMode_ = point.altitudeMode();
      
    // array constructor
    } else if (geo.util.isArray(point)) {
      pointArraySrc = point;
    
    // constructor from an Earth API object
    } else if (isEarthAPIObject_(point)) {
      var type = point.getType();
      
      // KmlPoint and KmlLookAt constructor
      if (type == 'KmlPoint' ||
          type == 'KmlLookAt') {
        this.lat_ = point.getLatitude();
        this.lng_ = point.getLongitude();
        this.altitude_ = point.getAltitude();
        this.altitudeMode_ = point.getAltitudeMode();
      
      // KmlCoord and KmlLocation constructor
      } else if (type == 'KmlCoord' ||
                 type == 'KmlLocation') {
        this.lat_ = point.getLatitude();
        this.lng_ = point.getLongitude();
        this.altitude_ = point.getAltitude();
      
      // Error, can't create a Point from any other Earth object
      } else {
        throw new TypeError(
            'Could not create a point from the given Earth object');
      }
    
    // GLatLng constructor
    } else if (isGLatLng_(point)) {
      this.lat_ = point.lat();
      this.lng_ = point.lng();

    // Error, can't create a Point from the single argument
    } else {
      throw new TypeError('Could not create a point from the given arguments');
    }
  
  // Assume each argument is a point coordinate, i.e.
  // new Point(0, 1, 2) ==> new Point([0, 1, 2])
  } else {
    pointArraySrc = arguments;
  }
  
  // construct from an array
  if (pointArraySrc) {
    for (var i = 0; i < pointArraySrc.length; i++) {
      if (typeof pointArraySrc[i] != 'number') {
        throw new TypeError('Coordinates must be numerical');
      }
    }
    
    this.lat_ = pointArraySrc[0];
    this.lng_ = pointArraySrc[1];
    if (pointArraySrc.length >= 3) {
      this.altitude_ = pointArraySrc[2];
      if (pointArraySrc.length >= 4) {
        this.altitudeMode_ = pointArraySrc[3];
      }
    }
  }

  // normalize
  this.lat_ = geo.math.normalizeLat(this.lat_);
  this.lng_ = geo.math.normalizeLng(this.lng_);
};

/**
 * The point's latitude, in degrees.
 * @type Number
 */
geo.Point.prototype.lat = function() {
  return this.lat_;
};
geo.Point.prototype.lat_ = 0;

/**
 * The point's longitude, in degrees.
 * @type Number
 */
geo.Point.prototype.lng = function() {
  return this.lng_;
};
geo.Point.prototype.lng_ = 0;

/**
 * The point's altitude, in meters.
 * @type Number
 */
geo.Point.prototype.altitude = function() {
  return this.altitude_;
};
geo.Point.prototype.altitude_ = 0;

/**
 * The point's altitude mode.
 * @type KmlAltitudeModeEnum
 */
geo.Point.prototype.altitudeMode = function() {
  return this.altitudeMode_;
};
geo.Point.prototype.altitudeMode_ = geo.ALTITUDE_RELATIVE_TO_GROUND;

/**
 * Returns the string representation of the point.
 * @type String
 */
geo.Point.prototype.toString = function() {
  return '(' + this.lat().toString() + ', ' + this.lng().toString() + ', ' +
      this.altitude().toString() + ')';
};

/**
 * Returns the 2D (no altitude) version of this point.
 * @type geo.Point
 */
geo.Point.prototype.flatten = function() {
  return new geo.Point(this.lat(), this.lng());
};

/**
 * Determines whether or not this point has an altitude component.
 * @type Boolean
 */
geo.Point.prototype.is3D = function() {
  return this.altitude_ !== 0;
};

/**
 * Determines whether or not the given point is the same as this one.
 * @param {geo.Point} otherPoint The other point.
 * @type Boolean
 */
geo.Point.prototype.equals = function(p2) {
  return this.lat() == p2.lat() &&
         this.lng() == p2.lng() &&
         this.altitude() == p2.altitude() &&
         this.altitudeMode() == p2.altitudeMode();
};

/**
 * Returns the angular distance between this point and the destination point.
 * @param {geo.Point} dest The destination point.
 * @see geo.math.angularDistance
 * @ignore
 */
geo.Point.prototype.angularDistance = function(dest) {
  return geo.math.angularDistance(this, dest);
};

/**
 * Returns the approximate sea level great circle (Earth) distance between
 * this point and the destination point using the Haversine formula and
 * assuming an Earth radius of geo.math.EARTH_RADIUS.
 * @param {geo.Point} dest The destination point.
 * @return {Number} The distance, in meters, to the destination point.
 * @see geo.math.distance
 */
geo.Point.prototype.distance = function(dest) {
  return geo.math.distance(this, dest);
};

/**
 * Calculates the initial heading/bearing at which an object at the start
 * point will need to travel to get to the destination point.
 * @param {geo.Point} dest The destination point.
 * @return {Number} The initial heading required to get to the destination
 *     point, in the [0,360) degree range.
 * @see geo.math.heading
 */
geo.Point.prototype.heading = function(dest) {
  return geo.math.heading(this, dest);
};

/**
 * Calculates an intermediate point on the geodesic between this point and the
 * given destination point.
 * @param {geo.Point} dest The destination point.
 * @param {Number} [fraction] The fraction of distance between the first
 *     and second points.
 * @return {geo.Point}
 * @see geo.math.midpoint
 */
geo.Point.prototype.midpoint = function(dest, fraction) {
  return geo.math.midpoint(this, dest, fraction);
};

/**
 * Calculates the destination point along a geodesic, given an initial heading
 * and distance, starting at this point.
 * @param {Object} options The heading and distance object literal.
 * @param {Number} options.heading The initial heading, in degrees.
 * @param {Number} options.distance The distance along the geodesic, in meters.
 * @return {geo.Point}
 * @see geo.math.destination
 */
geo.Point.prototype.destination = function(options) {
  return geo.math.destination(this, options);
};

/**
 * Returns the cartesian representation of the point, as a 3-vector,
 * assuming a spherical Earth of radius geo.math.EARTH_RADIUS.
 * @return {geo.linalg.Vector}
 */
geo.Point.prototype.toCartesian = function() {
  var sin_phi = Math.sin(this.lng().toRadians());
  var cos_phi = Math.cos(this.lng().toRadians());
  var sin_lmd = Math.sin(this.lat().toRadians());
  var cos_lmd = Math.cos(this.lat().toRadians());

  var r = geo.math.EARTH_RADIUS + this.altitude();
  return new geo.linalg.Vector([r * cos_phi * cos_lmd,
                                r * sin_lmd,
                                r * -sin_phi * cos_lmd]);
};

/**
 * A static method to create a point from a 3-vector representing the cartesian
 * coordinates of a point on the Earth, assuming a spherical Earth of radius
 * geo.math.EARTH_RADIUS.
 * @param {geo.linalg.Vector} cartesianVector The cartesian representation of
 *     the point to create.
 * @return {geo.Point} The point, or null if the point doesn't exist.
 */
geo.Point.fromCartesian = function(cartesianVector) {
  var r = cartesianVector.distanceFrom(geo.linalg.Vector.Zero(3));
  var unitVector = cartesianVector.toUnitVector();
  
  var altitude = r - geo.math.EARTH_RADIUS;
  
  var lat = Math.asin(unitVector.e(2)).toDegrees();
  if (lat > 90) {
    lat -= 180;
  }
  
  var lng = 0;
  if (Math.abs(lat) < 90) {
    lng = -Math.atan2(unitVector.e(3), unitVector.e(1)).toDegrees();
  }
  
  return new geo.Point(lat, lng, altitude);
};
/**
 * Create a new bounds object from the given parameters.
 * @param {geo.Bounds|geo.Point} [swOrBounds] Either an existing bounds object
 *     to copy, or the southwest, bottom coordinate of the new bounds object.
 * @param {geo.Point} [ne] The northeast, top coordinate of the new bounds
 *     object.
 * @constructor
 */
geo.Bounds = function() {
  // TODO: accept instances of GLatLngBounds

  // 1 argument constructor
  if (arguments.length == 1) {
    // copy constructor
    if (arguments[0].constructor === geo.Bounds) {
      var bounds = arguments[0];
      this.sw_ = new geo.Point(bounds.southWestBottom());
      this.ne_ = new geo.Point(bounds.northEastTop());

    // anything else, treated as the lone coordinate
    // TODO: accept array of points, a Path, or a Polygon
    } else {
      this.sw_ = this.ne_ = new geo.Point(arguments[0]);

    }

  // Two argument constructor -- a northwest and southeast coordinate
  } else if (arguments.length == 2) {
    var sw = new geo.Point(arguments[0]);
    var ne = new geo.Point(arguments[1]);

    // handle degenerate cases
    if (!sw && !ne) {
      return;
    } else if (!sw) {
      sw = ne;
    } else if (!ne) {
      ne = sw;
    }

    if (sw.lat() > ne.lat()) {
      throw new RangeError('Bounds southwest coordinate cannot be north of ' +
                           'the northeast coordinate');
    }

    if (sw.altitude() > ne.altitude()) {
      throw new RangeError('Bounds southwest coordinate cannot be north of ' +
                           'the northeast coordinate');
    }

    // TODO: check for incompatible altitude modes

    this.sw_ = sw;
    this.ne_ = ne;
  }
};

/**
 * The bounds' southwest, bottom coordinate.
 * @type geo.Point
 */
geo.Bounds.prototype.southWestBottom = function() {
  return this.sw_;
};
geo.Bounds.prototype.sw_ = null;

/**
 * The bounds' south coordinate.
 * @type Number
 */
geo.Bounds.prototype.south = function() {
  return !this.isEmpty() ? this.sw_.lat() : null;
};

/**
 * The bounds' west coordinate.
 * @type Number
 */
geo.Bounds.prototype.west = function() {
  return !this.isEmpty() ? this.sw_.lng() : null;
};

/**
 * The bounds' minimum altitude.
 * @type Number
 */
geo.Bounds.prototype.bottom = function() {
  return !this.isEmpty() ? this.sw_.altitude() : null;
};

/**
 * The bounds' northeast, top coordinate.
 * @type geo.Point
 */
geo.Bounds.prototype.northEastTop = function() {
  return this.ne_;
};
geo.Bounds.prototype.ne_ = null;

/**
 * The bounds' north coordinate.
 * @type Number
 */
geo.Bounds.prototype.north = function() {
  return !this.isEmpty() ? this.ne_.lat() : null;
};

/**
 * The bounds' east coordinate.
 * @type Number
 */
geo.Bounds.prototype.east = function() {
  return !this.isEmpty() ? this.ne_.lng() : null;
};

/**
 * The bounds' maximum altitude.
 * @type Number
 */
geo.Bounds.prototype.top = function() {
  return !this.isEmpty() ? this.ne_.altitude() : null;
};

/**
 * Returns whether or not the bounds intersect the antimeridian.
 * @type Boolean
 */
geo.Bounds.prototype.crossesAntimeridian = function() {
  return !this.isEmpty() && (this.sw_.lng() > this.ne_.lng());
};

/**
 * Returns whether or not the bounds have an altitude component.
 * @type Boolean
 */
geo.Bounds.prototype.is3D = function() {
  return !this.isEmpty() && (this.sw_.is3D() || this.ne_.is3D());
};

/**
 * Returns whether or not the given point is inside the bounds.
 * @param {geo.Point} point The point to test.
 * @type Boolean
 */
geo.Bounds.prototype.containsPoint = function(point) {
  point = new geo.Point(point);
  
  if (this.isEmpty()) {
    return false;
  }

  // check latitude
  if (!(this.south() <= point.lat() && point.lat() <= this.north())) {
    return false;
  }

  // check altitude
  if (this.is3D() && !(this.bottom() <= point.altitude() &&
                       point.altitude() <= this.top())) {
    return false;
  }

  // check longitude
  return this.containsLng_(point.lng());
};

/**
 * Returns whether or not the given line of longitude is inside the bounds.
 * @private
 * @param {Number} lng The longitude to test.
 * @type Boolean
 */
geo.Bounds.prototype.containsLng_ = function(lng) {
  if (this.crossesAntimeridian()) {
    return (lng <= this.east() || lng >= this.west());
  } else {
    return (this.west() <= lng && lng <= this.east());
  }
};

/**
 * Gets the longitudinal span of the given west and east coordinates.
 * @private
 * @param {Number} west
 * @param {Number} east
 */
function lngSpan_(west, east) {
  return (west > east) ? (east + 360 - west) : (east - west);
}

/**
 * Extends the bounds object by the given point, if the bounds don't already
 * contain the point. Longitudinally, the bounds will be extended either east
 * or west, whichever results in a smaller longitudinal span.
 * @param {geo.Point} point The point to extend the bounds by.
 */
geo.Bounds.prototype.extend = function(point) {
  point = new geo.Point(point);
  
  if (this.containsPoint(point)) {
    return;
  }

  if (this.isEmpty()) {
    this.sw_ = this.ne_ = point;
    return;
  }

  // extend up or down
  var newBottom = this.bottom();
  var newTop = this.top();

  if (this.is3D()) {
    newBottom = Math.min(newBottom, point.altitude());
    newTop = Math.max(newTop, point.altitude());
  }

  // extend north or south
  var newSouth = Math.min(this.south(), point.lat());
  var newNorth = Math.max(this.north(), point.lat());

  var newWest = this.west();
  var newEast = this.east();

  if (!this.containsLng_(point.lng())) {
    // try extending east and try extending west, and use the one that
    // has the smaller longitudinal span
    var extendEastLngSpan = lngSpan_(newWest, point.lng());
    var extendWestLngSpan = lngSpan_(point.lng(), newEast);

    if (extendEastLngSpan <= extendWestLngSpan) {
      newEast = point.lng();
    } else {
      newWest = point.lng();
    }
  }

  // update the bounds' coordinates
  this.sw_ = new geo.Point(newSouth, newWest, newBottom);
  this.ne_ = new geo.Point(newNorth, newEast, newTop);
};

/**
 * Returns the bounds' latitude, longitude, and altitude span as an object
 * literal.
 * @return {Object} Returns an object literal containing `lat`, `lng`, and
 *     `altitude` properties. Altitude will be null in the case that the bounds
 *     aren't 3D.
 */
geo.Bounds.prototype.span = function() {
  if (this.isEmpty()) {
    return {lat: 0, lng: 0, altitude: 0};
  }
  
  return {
    lat: (this.ne_.lat() - this.sw_.lat()),
    lng: lngSpan_(this.sw_.lng(), this.ne_.lng()),
    altitude: this.is3D() ? (this.ne_.altitude() - this.sw_.altitude()) : null
  };
};

/**
 * Determines whether or not the bounds object is empty, i.e. whether or not it
 * has no known associated points.
 * @type Boolean
 */
geo.Bounds.prototype.isEmpty = function() {
  return (this.sw_ === null && this.sw_ === null);
};

/**
 * Gets the center of the bounds.
 * @type geo.Point
 */
geo.Bounds.prototype.center = function() {
  if (this.isEmpty()) {
    return null;
  }

  return new geo.Point(
    (this.sw_.lat() + this.ne_.lat()) / 2,
    this.crossesAntimeridian() ?
        geo.math.normalizeLng(
            this.sw_.lng() +
            lngSpan_(this.sw_.lng(), this.ne_.lng()) / 2) :
        (this.sw_.lng() + this.ne_.lng()) / 2,
    (this.sw_.altitude() + this.ne_.altitude()) / 2);
};

// backwards compat
geo.Bounds.prototype.getCenter = geo.Bounds.prototype.center;

/**
 * Determines whether or not the bounds occupy the entire latitudinal range.
 * @type Boolean
 */
geo.Bounds.prototype.isFullLat = function() {
  return !this.isEmpty() && (this.south() == -90 && this.north() == 90);
};

/**
 * Determines whether or not the bounds occupy the entire longitudinal range.
 * @type Boolean
 */
geo.Bounds.prototype.isFullLng = function() {
  return !this.isEmpty() && (this.west() == -180 && this.east() == 180);
};

// TODO: equals(other)
// TODO: intersects(other)
// TODO: containsBounds(other)
/**
 * Creates a new path from the given parameters.
 * @param {geo.Path|geo.Point[]|PointSrc[]|KmlLineString|GPolyline|GPolygon}
 *     path The path data.
 * @constructor
 */
geo.Path = function() {
  this.coords_ = []; // don't use mutable objects in global defs
  var coordArraySrc = null;
  var i, n;
  
  // 1 argument constructor
  if (arguments.length == 1) {
    var path = arguments[0];
    
    // copy constructor
    if (path.constructor === geo.Path) {
      for (i = 0; i < path.numCoords(); i++) {
        this.coords_.push(new geo.Point(path.coord(i)));
      }
    
    // array constructor
    } else if (geo.util.isArray(path)) {
      coordArraySrc = path;
    
    // construct from Earth API object
    } else if (isEarthAPIObject_(path)) {
      var type = path.getType();
      
      // contruct from KmlLineString
      if (type == 'KmlLineString' ||
          type == 'KmlLinearRing') {
        n = path.getCoordinates().getLength();
        for (i = 0; i < n; i++) {
          this.coords_.push(new geo.Point(path.getCoordinates().get(i)));
        }
      
      // can't construct from the passed-in Earth object
      } else {
        throw new TypeError(
            'Could not create a path from the given arguments');
      }
    
    // GPolyline or GPolygon constructor
    } else if ('getVertex' in path && 'getVertexCount' in path) {
      n = path.getVertexCount();
      for (i = 0; i < n; i++) {
        this.coords_.push(new geo.Point(path.getVertex(i)));
      }
    
    // can't construct from the given argument
    } else {
      throw new TypeError('Could not create a path from the given arguments');
    }
  
  // Assume each argument is a PointSrc, i.e.
  // new Path(p1, p2, p3) ==>
  //    new Path([new Point(p1), new Point(p2), new Point(p3)])
  } else {
    coordArraySrc = arguments;
  }
  
  // construct from an array (presumably of PointSrcs)
  if (coordArraySrc) {
    for (i = 0; i < coordArraySrc.length; i++) {
      this.coords_.push(new geo.Point(coordArraySrc[i]));
    }
  }
};

/**#@+
  @field
*/

/**
 * The path's coordinates array.
 * @type Number
 * @private
 */
geo.Path.prototype.coords_ = null; // don't use mutable objects here

/**#@-*/

/**
 * Returns the string representation of the path.
 * @type String
 */
geo.Path.prototype.toString = function() {
  return '[' + this.coords_.map(function(p) {
                                  return p.toString();
                                }).join(', ') + ']';
};

/**
 * Determines whether or not the given path is the same as this one.
 * @param {geo.Path} otherPath The other path.
 * @type Boolean
 */
geo.Path.prototype.equals = function(p2) {
  for (var i = 0; i < p2.numCoords(); i++) {
    if (!this.coord(i).equals(p2.coord(i))) {
      return false;
    }
  }
  
  return true;
};

/**
 * Returns the number of coords in the path.
 */
geo.Path.prototype.numCoords = function() {
  return this.coords_.length;
};

/**
 * Returns the coordinate at the given index in the path.
 * @param {Number} index The index of the coordinate.
 * @type geo.Point
 */
geo.Path.prototype.coord = function(i) {
  // TODO: bounds check
  return this.coords_[i];
};

/**
 * Prepends the given coordinate to the path.
 * @param {geo.Point|PointSrc} coord The coordinate to prepend.
 */
geo.Path.prototype.prepend = function(coord) {
  this.coords_.unshift(new geo.Point(coord));
};

/**
 * Appends the given coordinate to the path.
 * @param {geo.Point|PointSrc} coord The coordinate to append.
 */
geo.Path.prototype.append = function(coord) {
  this.coords_.push(new geo.Point(coord));
};

/**
 * Inserts the given coordinate at the i'th index in the path.
 * @param {Number} index The index to insert into.
 * @param {geo.Point|PointSrc} coord The coordinate to insert.
 */
geo.Path.prototype.insert = function(i, coord) {
  // TODO: bounds check
  this.coords_.splice(i, 0, new geo.Point(coord));
};

/**
 * Removes the coordinate at the i'th index from the path.
 * @param {Number} index The index of the coordinate to remove.
 */
geo.Path.prototype.remove = function(i) {
  // TODO: bounds check
  this.coords_.splice(i, 1);
};

/**
 * Returns a sub path, containing coordinates starting from the
 * startIndex position, and up to but not including the endIndex
 * position.
 * @type geo.Path
 */
geo.Path.prototype.subPath = function(startIndex, endIndex) {
  return this.coords_.slice(startIndex, endIndex);
};

/**
 * Reverses the order of the path's coordinates.
 */
geo.Path.prototype.reverse = function() {
  this.coords_.reverse();
};

/**
 * Calculates the total length of the path using great circle distance
 * calculations.
 * @return {Number} The total length of the path, in meters.
 */
geo.Path.prototype.distance = function() {
  var dist = 0;
  for (var i = 0; i < this.coords_.length - 1; i++) {
    dist += this.coords_[i].distance(this.coords_[i + 1]);
  }
  
  return dist;
};

/**
 * Returns whether or not the path, when closed, contains the given point.
 * Thanks to Mike Williams of http://econym.googlepages.com/epoly.htm and
 * http://alienryderflex.com/polygon/ for this code.
 * @param {geo.Point} point The point to test.
 */
geo.Path.prototype.containsPoint = function(point) {
  var oddNodes = false;
  var y = point.lat();
  var x = point.lng();
  for (var i = 0; i < this.coords_.length; i++) {
    var j = (i + 1) % this.coords_.length;
    if (((this.coords_[i].lat() < y && this.coords_[j].lat() >= y) ||
         (this.coords_[j].lat() < y && this.coords_[i].lat() >= y)) &&
        (this.coords_[i].lng() + (y - this.coords_[i].lat()) /
            (this.coords_[j].lat() - this.coords_[i].lat()) *
            (this.coords_[j].lng() - this.coords_[i].lng()) < x)) {
      oddNodes = !oddNodes;
    }
  }
  
  return oddNodes;
};

/**
 * Returns the latitude/longitude bounds wholly containing this path.
 * @type geo.Bounds
 */
geo.Path.prototype.bounds = function() {
  if (!this.numCoords()) {
    return new geo.Bounds();
  }

  var bounds = new geo.Bounds(this.coord(0));

  // TODO: optimize
  var numCoords = this.numCoords();
  for (var i = 1; i < numCoords; i++) {
    bounds.extend(this.coord(i));
  }

  return bounds;
};
// TODO: unit test

/**
 * Returns the signed approximate area of the polygon formed by the path when
 * the path is closed.
 * @see http://econym.org.uk/gmap/epoly.htm
 * @private
 */
geo.Path.prototype.signedArea_ = function() {
  var a = 0;
  var b = this.bounds();
  var x0 = b.west();
  var y0 = b.south();

  var numCoords = this.numCoords();
  for (var i = 0; i < numCoords; i++) {
    var j = (i + 1) % numCoords;
    var x1 = this.coord(i).distance(new geo.Point(this.coord(i).lat(), x0));
    var x2 = this.coord(j).distance(new geo.Point(this.coord(j).lat(), x0));
    var y1 = this.coord(i).distance(new geo.Point(y0, this.coord(i).lng()));
    var y2 = this.coord(j).distance(new geo.Point(y0, this.coord(j).lng()));
    a += x1 * y2 - x2 * y1;
  }

  return a * 0.5;
};

/**
 * Returns the approximate area of the polygon formed by the path when the path
 * is closed.
 * @return {Number} The approximate area, in square meters.
 * @see http://econym.org.uk/gmap/epoly.htm
 * @note This method only works with non-intersecting polygons.
 * @note The method is inaccurate for large regions because the Earth's
 *     curvature is not accounted for.
 */
geo.Path.prototype.area = function() {
  return Math.abs(this.signedArea_());
};
// TODO: unit test

/**
 * Returns whether or not the coordinates of the polygon formed by the path when
 * the path is closed are in counter clockwise order.
 * @type Boolean
 */
geo.Path.prototype.isCounterClockwise_ = function() {
  return Boolean(this.signedArea_() >= 0);
};
/**
 * Creates a new polygon from the given parameters.
 * @param {geo.Polygon|geo.Path} outerBoundary
 *     The polygon's outer boundary.
 * @param {geo.Path[]} [innerBoundaries]
 *     The polygon's inner boundaries, if any.
 * @constructor
 */
geo.Polygon = function() {
  this.outerBoundary_ = new geo.Path();
  this.innerBoundaries_ = [];
  var i;
  
  // 0 argument constructor
  if (arguments.length === 0) {
    
  // 1 argument constructor
  } else if (arguments.length == 1) {
    var poly = arguments[0];
    
    // copy constructor
    if (poly.constructor === geo.Polygon) {
      this.outerBoundary_ = new geo.Path(poly.outerBoundary());
      for (i = 0; i < poly.innerBoundaries().length; i++) {
        this.innerBoundaries_.push(new geo.Path(poly.innerBoundaries()[i]));
      }
    
    // construct from Earth API object
    } else if (isEarthAPIObject_(poly)) {
      var type = poly.getType();

      // construct from KmlLineString
      if (type == 'KmlLineString' ||
          type == 'KmlLinearRing') {
        this.outerBoundary_ = new geo.Path(poly);
      
      // construct from KmlPolygon
      } else if (type == 'KmlPolygon') {
        this.outerBoundary_ = new geo.Path(poly.getOuterBoundary());
        
        var ibChildNodes = poly.getInnerBoundaries().getChildNodes();
        var n = ibChildNodes.getLength();
        for (i = 0; i < n; i++) {
          this.innerBoundaries_.push(new geo.Path(ibChildNodes.item(i)));
        }
      
      // can't construct from the passed-in Earth object
      } else {
        throw new TypeError(
            'Could not create a polygon from the given arguments');
      }
    
    // treat first argument as an outer boundary path
    } else {
      this.outerBoundary_ = new geo.Path(arguments[0]);
    }
  
  // multiple argument constructor, either:
  // - arrays of numbers (outer boundary coords)
  // - a path (outer boundary) and an array of paths (inner boundaries)
  } else {
    if (arguments[0].length && typeof arguments[0][0] == 'number') {
      // ...new geo.Polygon([0,0], [1,1], [2,2]...
      this.outerBoundary_ = new geo.Path(arguments);
    } else if (arguments[1]) {
      // ...new geo.Polygon([ [0,0] ... ], [ [ [0,0], ...
      this.outerBoundary_ = new geo.Path(arguments[0]);
      if (!geo.util.isArray(arguments[1])) {
        throw new TypeError('Second argument to geo.Polygon constructor ' +
                            'must be an array of paths.');
      }
      
      for (i = 0; i < arguments[1].length; i++) {
        this.innerBoundaries_.push(new geo.Path(arguments[1][i]));
      }
    } else {
      throw new TypeError('Cannot create a path from the given arguments.');
    }
  }
};

/**#@+
  @field
*/

/**
 * The polygon's outer boundary (path).
 * @type {geo.Path}
 * @private
 */
geo.Polygon.prototype.outerBoundary_ = null;

/**
 * The polygon's inner boundaries.
 * @type {geo.Path[]}
 * @private
 */
geo.Polygon.prototype.innerBoundaries_ = null; // don't use mutable objects

/**#@-*/

/**
 * Returns the string representation of the polygon, useful primarily for
 * debugging purposes.
 * @type String
 */
geo.Polygon.prototype.toString = function() {
  return 'Polygon: ' + this.outerBoundary().toString() +
      (this.innerBoundaries().length ?
        ', (' + this.innerBoundaries().length + ' inner boundaries)' : '');
};


/**
 * Returns the polygon's outer boundary path.
 * @type geo.Path
 */
geo.Polygon.prototype.outerBoundary = function() {
  return this.outerBoundary_;
};

/**
 * Returns an array containing the polygon's inner boundaries.
 * You may freely add or remove geo.Path objects to this array.
 * @type geo.Path[]
 */
geo.Polygon.prototype.innerBoundaries = function() {
  return this.innerBoundaries_;
};
// TODO: deprecate writability to this in favor of addInnerBoundary and
// removeInnerBoundary

/**
 * Returns whether or not the polygon contains the given point.
 * @see geo.Path.containsPoint
 * @see http://econym.googlepages.com/epoly.htm
 */
geo.Polygon.prototype.containsPoint = function(point) {
  // outer boundary should contain the point
  if (!this.outerBoundary_.containsPoint(point)) {
    return false;
  }
  
  // none of the inner boundaries should contain the point
  for (var i = 0; i < this.innerBoundaries_.length; i++) {
    if (this.innerBoundaries_[i].containsPoint(point)) {
      return false;
    }
  }
  
  return true;
};

/**
 * Returns the latitude/longitude bounds wholly containing this polygon.
 * @type geo.Bounds
 */
geo.Polygon.prototype.bounds = function() {
  return this.outerBoundary_.bounds();
};

/**
 * Returns the approximate area of the polygon.
 * @return {Number} The approximate area, in square meters.
 * @see geo.Path.area
 */
geo.Polygon.prototype.area = function() {
  // start with outer boundary area
  var area = this.outerBoundary_.area();
  
  // subtract inner boundary areas
  // TODO: handle double counting of intersections
  for (var i = 0; i < this.innerBoundaries_.length; i++) {
    area -= this.innerBoundaries_[i].area();
  }
  
  return area;
};

/**
 * Returns whether or not the polygon's outer boundary coordinates are
 * in counter clockwise order.
 * @type Boolean
 */
geo.Polygon.prototype.isCounterClockwise = function() {
  return this.outerBoundary_.isCounterClockwise_();
};

/**
 * Ensures that the polygon's outer boundary coordinates are in counter
 * clockwise order by reversing them if they are counter clockwise.
 * @see geo.Polygon.isCounterClockwise
 */
geo.Polygon.prototype.makeCounterClockwise = function() {
  if (this.isCounterClockwise()) {
    this.outerBoundary_.reverse();
  }
};
/**
 * The geo.util namespace contains generic JavaScript and JS/Geo utility
 * functions.
 * @namespace
 */
geo.util = {isnamespace_:true};

/**
 * Determines whether or not the object is `undefined`.
 * @param {Object} object The object to test.
 * @note Taken from Prototype JS library
 */
geo.util.isUndefined = function(object) {
  return typeof object == 'undefined';
};

/**
 * Determines whether or not the object is a JavaScript array.
 * @param {Object} object The object to test.
 * @note Taken from Prototype JS library
 */
geo.util.isArray = function(object) {
  return object !== null && typeof object == 'object' &&
      'splice' in object && 'join' in object;
};

/**
 * Determines whether or not the object is a JavaScript function.
 * @param {Object} object The object to test.
 * @note Taken from Prototype JS library
 */
geo.util.isFunction = function(object) {
  return object !== null && typeof object == 'function' &&
      'call' in object && 'apply' in object;
};

/**
 * Determines whether or not the given object is an Earth API object.
 * @param {Object} object The object to test.
 * @private
 */
function isEarthAPIObject_(object) {
  return object !== null &&
      (typeof object == 'function' || typeof object == 'object') &&
      'getType' in object;
}

/**
 * Determines whether or not the object is an object literal (a.k.a. hash).
 * @param {Object} object The object to test.
 */
geo.util.isObjectLiteral = function(object) {
  return object !== null && typeof object == 'object' &&
      object.constructor === Object && !isEarthAPIObject_(object);
};

/**
 * Determins whether or not the given object is a google.maps.LatLng object
 * (GLatLng).
 */
function isGLatLng_(object) {
  return (window.google &&
          window.google.maps &&
          window.google.maps.LatLng &&
          object.constructor === window.google.maps.LatLng);
}
window.geo = geo;
})();
