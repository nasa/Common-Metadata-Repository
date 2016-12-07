def totalOverlap = 0;
for (range in temporalRanges)
{
 def overlapStartDate = range.start_date;
 if (doc['start-date'].value != 0 && doc['start-date'].value > overlapStartDate)
  { overlapStartDate = doc['start-date'].value; }
 def overlapEndDate = range.end_date;
 if (doc['end-date'].value != 0 && doc['end-date'].value < overlapEndDate)
  { overlapEndDate = doc['end-date'].value; }
 if (overlapEndDate > overlapStartDate)
  { totalOverlap += overlapEndDate - overlapStartDate; }
}
if (rangeSpan > 0) { totalOverlap / rangeSpan; }
else { 0; }
