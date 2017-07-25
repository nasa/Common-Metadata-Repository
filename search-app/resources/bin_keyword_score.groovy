/* Elastic groovy script to round the keyword score (_score) to the nearest binSize.
   Divide score by 2 so that it matches what is output in search and the bin size
   can be assessed based on that number (otherwise bin size would have to be doubled).
   Round to 2 decimal places to avoid slight differences from math, which would defeat
   the purpose of the script.
*/

def binnedScore = binSize * (double)Math.round((_score / 2.0) / binSize);
binnedScore.round(2);
