/* Elastic groovy script to round the community usage to the nearest binSize.
   Round to 2 decimal places to avoid slight differences from math, which would defeat
   the purpose of the script.
*/

def binnedScore = binSize * (double)Math.round(doc['usage-relevancy-score'].value / binSize);
binnedScore.round(2);
