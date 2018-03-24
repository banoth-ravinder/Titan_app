package com.lannbox.rfduinotest;

import android.util.Log;

import com.badlogic.gdx.audio.analysis.FFT;

import static android.content.ContentValues.TAG;
import static java.lang.Math.abs;

public class EOGFilter {


    private final int FFT_SIZE = 1024;
    public int count=0;

    double Series[];
    double NormalizedSeries[];
    int Size;
    int Pos;
    int PrevPos;
    boolean Full;
    int HeartRate;

    EOGFilter(int windowSize) {
        Size = windowSize;
        Series = new double[windowSize];
        NormalizedSeries = new double[windowSize];
        Pos = 0;
        Full = false;
        HeartRate = 0;
    }

    double InputData(double data) {
        if (!Full) {
            Full = (Pos == Size - 1);
        }

        PrevPos = Pos;
        Series[Pos++] = data;
        Pos = Pos % Size;

        double normalized = 0;
        if (Full) {
            //
            // Normalize data.
            //
            normalized = data - SeriesAverage();
            NormalizedSeries[Pos] = normalized;
        }
        return normalized;
    }

    private double SeriesAverage() {
        double sum = 0;
        for (double d : Series) sum += d;

        return sum / Size;
    }

    Number[] ComputeFFT() {
        Number[] series2Data = new Number[FFT_SIZE];

        if (NormalizedSeries != null && Full) {
            float[] samples_float = new float[NormalizedSeries.length];

            float[] new_array = new float[FFT_SIZE];

            for (int i = 0; i < NormalizedSeries.length; i++) {
                samples_float[i] = (float) NormalizedSeries[i];
            }

            FFT fft1 = new FFT(FFT_SIZE, 100);

            // Zero Pad signal
            for (int i = 0; i < 200; i++) {

                if (i < samples_float.length) {
                    new_array[i] = samples_float[i];
                } else {
                    new_array[i] = 0;
                }
            }

            fft1.forward(new_array);

            float[] imag = fft1.getImaginaryPart();
            float[] real = fft1.getRealPart();
            float max = 0;
            HeartRate = 0;

            //
            // Compute the FTT for range 10 - 212
            //
            for (int i = 10; i < 212; i++) {

                series2Data[i] = (float) Math.sqrt((real[i] * real[i]) + (imag[i] * imag[i]));
            }

            //
            // Return the max amplitude in the acceptable range as HR
            //
            for (int i = 10; i < 212; i++) {

                if (series2Data[i].floatValue() > max) {
                    max = series2Data[i].floatValue();
                    HeartRate = i;
                }
            }
        }

        return series2Data;
    }

    Number[] ComputeBR() {
        Number[] aa = new Number[FFT_SIZE];

        if (NormalizedSeries != null && Full) {



            float[] samples_float = new float[NormalizedSeries.length];

            float[] new_array = new float[FFT_SIZE];

            for (int i = 0; i < NormalizedSeries.length; i++) {
                samples_float[i] = (float) NormalizedSeries[i];
            }


            count=0;

            // Zero Pad signal
            for (int i = 0; i < 199; i++) {
                if (i < samples_float.length) {
                    new_array[i] = samples_float[i + 1] - samples_float[i];
                } else {
                    new_array[i] = 0;
                }
            }
            for (int i = 0; i < 200; i++) {
                if (abs(new_array[i]) > 0.075) {
                    aa[i] = 1;
                    Log.i(TAG,"111");
                } else {
                    aa[i] = 0;
                    Log.i(TAG,"000");
                }
            }

            int sum=0,i=0;
            while(i<200) {
                if (aa[i].floatValue()==1) {
                    sum=sum+1;
                }
                else{
                    if(sum>2){
                        int max= Integer.MIN_VALUE;
                        for(int j=i-sum; j < i; j++)
                            if ( aa[j].floatValue() > max )
                                max = aa[j].intValue();
                    }
                    sum=0;
                }
                i++;
            }





//            for (int i = 1; i < 200; i++) {
//                Log.i(TAG,"***");
//                if (aa[i] - aa[i - 1] == 1) {
//                    count =count + 1;
//                }
//            }

            Log.i(TAG,"COUNT :"+ count);

        }

        return aa;

    }


    public static double min(float[] arr) {
        return arr[imin(arr)];
    }

    public static double max(float[] arr) {
        return arr[imax(arr)];
    }

    public static int imin(float[] arr) {
        int i = 0;
        for (int j = 1; j < arr.length; j++)
            if (arr[j] < arr[i])
                i = j;
        return i;
    } // imin()

    public static int imax(float[] arr) {
        int i = 0;
        for (int j = 1; j < arr.length; j++)
            if (arr[j] > arr[i])
                i = j;
        return i;
    } // imax()
}
