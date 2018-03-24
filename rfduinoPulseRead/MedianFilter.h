#ifndef MedianFilter_h
#define MedianFilter_h

#if defined(ARDUINO) && ARDUINO >= 100
#include "Arduino.h"
#else
#include "WProgram.h"
#endif

#define MEDIAN_MIN_SIZE     1
#define MEDIAN_MAX_SIZE     19

#include <inttypes.h>

class MedianFilter
{
public:
    MedianFilter(uint8_t size);
    ~MedianFilter();

    void addData(double data);
    double getMedian();

protected:
    double* m_series;
    int m_size;
    int m_pos;

    void sort(double a[], int size);
};

#endif
// END OF FILE
