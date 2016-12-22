/* Elastic groovy script to calculate the temporal overlap of a collection over the input ranges.
   Collection start and end dates are doc['start-date'] and doc['end-date'].
   For each temporal range, calculate the amount of overlap for the collection. Add them up
   and divide by the sum of the span of the ranges (rangeSpan) to get the overall overlap.
*/

def totalOverlap = 0;
for (range in temporalRanges)
{
 def overlapStartDate = range.start_date;
 if (doc['start-date'].empty == false && doc['start-date'].value > overlapStartDate)
 {
   overlapStartDate = doc['start-date'].value;
 }
 def overlapEndDate = range.end_date;
 if (doc['end-date'].empty == false && doc['end-date'].value < overlapEndDate)
 {
   overlapEndDate = doc['end-date'].value;
 }
 if (overlapEndDate > overlapStartDate)
 {
   totalOverlap += overlapEndDate - overlapStartDate;
 }
}
if (rangeSpan > 0) { totalOverlap / rangeSpan; }
else
{
  0; /* Temporal overlap is 0 */
}
