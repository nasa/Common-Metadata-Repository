/* Elastic groovy script to calculate the temporal overlap of a collection over the input ranges.

   Collection temporal ranges are in doc['temporal-ranges'], which is a list of dates with no
   overlaps. When sorted, they will be in the order: start-date, end-date, start-date, end-date...
   If the list has an odd number of elements, the collection is ongoing and has no end date.

   For each temporal range, calculate the amount of overlap for the collection. Add them up
   and divide by the sum of the span of the ranges (rangeSpan) to get the overall overlap.
*/

def totalOverlap = 0;
def sortedCollectionRanges = doc['temporal-ranges'].values.toSorted();
for (range in temporalRanges)
{
  for (i = 0; i < sortedCollectionRanges.size(); i+= 2)
  {
     def overlapStartDate = range.start_date;
     if (sortedCollectionRanges[i] > overlapStartDate)
     {
       overlapStartDate = sortedCollectionRanges[i];
     }

     def overlapEndDate = range.end_date;
     if ((i + 1) < sortedCollectionRanges.size() && sortedCollectionRanges[i+1] < overlapEndDate)
     {
       overlapEndDate = sortedCollectionRanges[i+1];
     }
     if (overlapEndDate > overlapStartDate)
     {
       totalOverlap += overlapEndDate - overlapStartDate;
     }
  }
}
if (rangeSpan > 0) { totalOverlap / rangeSpan; }
else
{
  0; /* Temporal overlap is 0 */
}
