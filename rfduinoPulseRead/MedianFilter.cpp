#include "MedianFilter.h"

MedianFilter::MedianFilter(uint8_t size)
{
    m_size = constrain(size, MEDIAN_MIN_SIZE, MEDIAN_MAX_SIZE);
    m_series = (double *) malloc(m_size * sizeof(double));
    
    m_pos = 0;
}

MedianFilter::~MedianFilter()
{
    free(m_series);
}

// adds a new value to the data-set
// and returns the 
void MedianFilter::addData(double value)
{
    m_series[m_pos++] = value;
    //Serial.print("m_series[pos-1]: ");
    //Serial.println(m_series[m_pos-1]);
    m_pos = m_pos % m_size;
    //Serial.print("m_pos: ");
    //Serial.println(m_pos);
}

double MedianFilter::getMedian()
{
    double tempArray[m_size];
    
    for(int i=0; i<m_size; i++) {
      tempArray[i] = m_series[i];
//      Serial.print("tempArray[]: ");
//      Serial.println(tempArray[i]);
    }

    sort(tempArray, m_size);
//    for(int i=0; i<m_size; i++) {
//      Serial.print("tempArray(sorted)[]: ");
//      Serial.println(tempArray[i]);
//    }
    double median;
    if (m_size % 2 == 0) {
        median = ( tempArray[m_size / 2] + tempArray[m_size / 2 - 1]) / 2;
    }
    else {
        median = tempArray[m_size / 2];
    }

    return median;
}

void MedianFilter::sort(double a[], int size) {
    for(int i=0; i<(size-1); i++) {
        for(int o=0; o<(size-(i+1)); o++) {
            if(a[o] > a[o+1]) {
                double t = a[o];
                a[o] = a[o+1];
                a[o+1] = t;
            }
        }
    }    
}

// END OF FILE
